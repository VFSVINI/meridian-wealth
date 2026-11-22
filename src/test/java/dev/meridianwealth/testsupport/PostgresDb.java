package dev.meridianwealth.testsupport;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * PostgreSQL de verdade (binário embutido, sem Docker) com as migrações do Flyway aplicadas, compartilhado por todos os testes
 * de persistência da JVM. Cada teste chama {@link #reset()} para começar com as tabelas vazias.
 */
public final class PostgresDb {
    private static EmbeddedPostgres pg;
    private static DataSource ds;

    private PostgresDb() {}

    public static synchronized DataSource dataSource() {
        if (ds == null) {
            try {
                pg = EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new IllegalStateException("Não foi possível iniciar o PostgreSQL embutido", e);
            }
            ds = pg.getPostgresDatabase();
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    pg.close();
                } catch (IOException ignored) {
                    // encerramento da JVM
                }
            }));
        }
        return ds;
    }

    /** URL JDBC do banco embutido (usuário "postgres", sem senha), para os testes que sobem a aplicação inteira. */
    public static String jdbcUrl() {
        dataSource();
        return pg.getJdbcUrl("postgres", "postgres");
    }

    /** Cria um banco vazio e separado no mesmo PostgreSQL embutido (sem migrações), para testar migrações em isolamento. */
    public static DataSource newDatabase(String name) {
        dataSource();
        new JdbcTemplate(ds).execute("DROP DATABASE IF EXISTS " + name);
        new JdbcTemplate(ds).execute("CREATE DATABASE " + name);
        return pg.getDatabase("postgres", name);
    }

    public static JdbcTemplate jdbc() { return new JdbcTemplate(dataSource()); }

    /** Esvazia as tabelas de dados de usuário (e as que dependem delas) entre testes. */
    public static void reset() {
        jdbc().execute("TRUNCATE TABLE ledger_entries, refresh_tokens, positions, users CASCADE");
    }
}
