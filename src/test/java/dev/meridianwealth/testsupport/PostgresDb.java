package dev.meridianwealth.testsupport;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * PostgreSQL de verdade com as migrações do Flyway aplicadas, compartilhado por todos os testes de persistência da JVM. Cada teste chama
 * {@link #reset()} para começar com as tabelas vazias.
 *
 * <p>Dois fundos, escolhidos por {@code -Dmw.test.postgres=}: o padrão é o binário embutido (zonky), que roda em qualquer máquina sem Docker;
 * com {@code container}, um PostgreSQL 16 num contêiner do Testcontainers (a CI roda os dois, e o contêiner prova que as garantias de
 * concorrência e idempotência valem fora do binário embutido). Sem Docker, o modo contêiner falha com a mensagem do Testcontainers.
 */
public final class PostgresDb {
    private static final boolean CONTAINER = "container".equalsIgnoreCase(System.getProperty("mw.test.postgres"));
    private static final String IMAGE = "postgres:16-alpine";

    private static EmbeddedPostgres pg;
    private static PostgreSQLContainer<?> container;
    private static DataSource ds;

    private PostgresDb() {}

    /** Os testes estão rodando contra o contêiner do Testcontainers? */
    public static boolean usingContainer() { return CONTAINER; }

    public static synchronized DataSource dataSource() {
        if (ds == null) {
            if (CONTAINER) startContainer();
            else startEmbedded();
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        }
        return ds;
    }

    private static void startEmbedded() {
        try {
            pg = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível iniciar o PostgreSQL embutido", e);
        }
        ds = pg.getPostgresDatabase();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
                // encerramento da JVM
            }
        }));
    }

    private static void startContainer() {
        container = new PostgreSQLContainer<>(IMAGE).withUsername("postgres").withPassword("postgres").withDatabaseName("postgres");
        container.start();
        ds = pool(container.getJdbcUrl());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (ds instanceof HikariDataSource h) h.close();
        }));
    }

    private static HikariDataSource pool(String url) {
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url);
        c.setUsername("postgres");
        c.setPassword("postgres");
        c.setMaximumPoolSize(24); // os testes de concorrência disparam dezenas de transações ao mesmo tempo
        return new HikariDataSource(c);
    }

    /** URL JDBC do banco (usuário e senha "postgres"), para os testes que sobem a aplicação inteira. */
    public static String jdbcUrl() {
        dataSource();
        return CONTAINER ? container.getJdbcUrl() : pg.getJdbcUrl("postgres", "postgres");
    }

    /** Cria um banco vazio e separado no mesmo PostgreSQL (sem migrações), para testar migrações em isolamento. */
    public static DataSource newDatabase(String name) {
        dataSource();
        new JdbcTemplate(ds).execute("DROP DATABASE IF EXISTS " + name);
        new JdbcTemplate(ds).execute("CREATE DATABASE " + name);
        if (CONTAINER) return pool(container.getJdbcUrl().replace("/" + container.getDatabaseName(), "/" + name));
        return pg.getDatabase("postgres", name);
    }

    public static JdbcTemplate jdbc() { return new JdbcTemplate(dataSource()); }

    /** Esvazia as tabelas de dados de usuário (e as que dependem delas) entre testes. */
    public static void reset() {
        jdbc().execute("TRUNCATE TABLE ledger_entries, refresh_tokens, positions, users CASCADE");
    }
}
