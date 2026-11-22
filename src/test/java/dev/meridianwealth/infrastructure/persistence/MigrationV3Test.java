package dev.meridianwealth.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.testsupport.PostgresDb;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A migração V3 sobre um banco que já tinha dados do item #12: cada posição existente ganha a entrada de abertura do livro, o custo em
 * reais de posição em BRL é preenchido e o de posição em dólar fica nulo, com o câmbio marcado como "desconhecido", nunca inventado.
 */
class MigrationV3Test {
    @Test void existingPositionsGetAnOpeningEntryAndTheLegacyDollarCostStaysUnknown() {
        DataSource ds = PostgresDb.newDatabase("legado_v3");
        Flyway upToV2 = Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("2").load();
        upToV2.migrate();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        UUID user = UUID.randomUUID();
        jdbc.update("INSERT INTO users(id, email, display_name, auth_provider, created_at) VALUES (?, 'legado@demo.invalid', 'Legado', 'DEMO', now())", user);
        String insert = "INSERT INTO positions(id, user_id, asset_key, symbol, asset_type, label, quantity, average_price, currency, created_at, updated_at, version) "
                + "VALUES (gen_random_uuid(), ?, ?, ?, ?, ?, ?, ?, ?, '2026-02-03 10:00:00-03', '2026-02-03 10:00:00-03', ?)";
        jdbc.update(insert, user, "PETR4", "PETR4", "ACAO", "PETR4 · Petrobras PN", new BigDecimal("10"), new BigDecimal("30"), "BRL", 3);
        jdbc.update(insert, user, "SPY", "SPY", "ETF", "SPY · S&P 500 ETF", new BigDecimal("2"), new BigDecimal("500"), "USD", 0);
        jdbc.update(insert, user, "T:LCI:lci-legado", null, "LCI", "LCI Legado", new BigDecimal("1"), new BigDecimal("10000"), "BRL", 1);

        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM positions", Integer.class)).as("as posições seguem intactas").isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_entries", Integer.class)).as("uma abertura por posição").isEqualTo(3);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT l.asset_key, l.entry_type, l.trade_date::text AS d, l.quantity, l.quantity_after, l.average_price_after, l.average_cost_brl_after, "
                + "l.fx_brl_per_usd, l.fx_source, l.version_after, p.version AS pv, p.average_cost_brl, p.average_price FROM ledger_entries l JOIN positions p ON p.id = l.position_id ORDER BY l.asset_key");
        assertThat(rows).extracting(r -> r.get("entry_type")).containsOnly("OPENING");
        Map<String, Object> petr = rows.stream().filter(r -> "PETR4".equals(r.get("asset_key"))).findFirst().orElseThrow();
        assertThat(petr.get("d")).isEqualTo("2026-02-03");
        assertThat((BigDecimal) petr.get("quantity_after")).isEqualByComparingTo("10");
        assertThat((BigDecimal) petr.get("average_cost_brl")).as("BRL: o custo em reais é o preço médio").isEqualByComparingTo("30");
        assertThat((BigDecimal) petr.get("average_cost_brl_after")).isEqualByComparingTo("30");
        assertThat(petr.get("fx_source")).isNull();
        assertThat(((Number) petr.get("version_after")).longValue()).isEqualTo(((Number) petr.get("pv")).longValue()).isEqualTo(3);
        Map<String, Object> spy = rows.stream().filter(r -> "SPY".equals(r.get("asset_key"))).findFirst().orElseThrow();
        assertThat(spy.get("average_cost_brl")).as("dólar sem câmbio de compra: não informado").isNull();
        assertThat(spy.get("average_cost_brl_after")).isNull();
        assertThat(spy.get("fx_brl_per_usd")).isNull();
        assertThat(spy.get("fx_source")).isEqualTo("UNKNOWN");
        assertThat((BigDecimal) spy.get("average_price_after")).isEqualByComparingTo("500");
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET fees = 1")).as("o gatilho vale também para o que a migração gravou").isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void theMigrationRunsOnAnEmptyDatabaseAndIsIdempotent() {
        DataSource ds = PostgresDb.newDatabase("vazio_v3");
        Flyway flyway = Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);
        assertThat(flyway.migrate().migrationsExecuted).as("segunda execução não faz nada").isZero();
        assertThat(new JdbcTemplate(ds).queryForObject("SELECT count(*) FROM ledger_entries", Integer.class)).isZero();
    }
}
