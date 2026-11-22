package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Livro de movimentações. É o <b>único caminho de escrita</b> da carteira: cada mudança de posição grava a entrada do livro e altera a
 * posição na mesma transação, ou nenhuma das duas. A posição é a dobra do livro. As entradas são só acrescentadas (o adaptador JDBC
 * bloqueia UPDATE por gatilho). A mesma {@code Idempotency-Key} do mesmo usuário nunca grava duas vezes.
 */
public interface PortfolioLedger {
    enum Op { OPEN, UPDATE, DELETE }

    /**
     * Uma mudança atômica: {@code OPEN} cria a posição {@code next}; {@code UPDATE} a substitui se a versão ainda for
     * {@code expectedVersion}; {@code DELETE} a remove (venda total ou baixa) sob a mesma condição.
     */
    record Change(Op op, Position next, UUID positionId, long expectedVersion, LedgerEntry entry) {}

    sealed interface Commit {
        /** Gravado. {@code position} é a posição depois da mudança, ou nulo se ela foi removida. */
        record Committed(Position position, LedgerEntry entry) implements Commit {}

        /** A versão mudou (ou a posição não existe mais): nada foi gravado. */
        record VersionConflict() implements Commit {}

        /** Já existe posição do usuário para este ativo: nada foi gravado. */
        record PositionExists() implements Commit {}

        /** Já existe entrada com esta {@code Idempotency-Key}: nada foi gravado e a existente é devolvida. */
        record Duplicate(LedgerEntry existing) implements Commit {}
    }

    Commit commit(Change change);

    Optional<LedgerEntry> findByKey(UUID userId, String idempotencyKey);

    /** Entradas do usuário em ordem de gravação, com filtros opcionais (ativo e intervalo de datas da operação, inclusivo). */
    List<LedgerEntry> entries(UUID userId, String assetKey, LocalDate from, LocalDate to, int limit, int offset);

    /** Todas as entradas do usuário em ordem de gravação (limitadas por segurança). */
    List<LedgerEntry> all(UUID userId);

    /** Data da última operação do ativo, para impedir lançamentos fora de ordem. */
    Optional<LocalDate> lastTradeDate(UUID userId, String assetKey);
}
