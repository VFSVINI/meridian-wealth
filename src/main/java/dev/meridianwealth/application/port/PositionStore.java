package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.portfolio.Position;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento de posições. Toda consulta e toda escrita recebem o dono: nenhuma operação alcança a posição de outro usuário. A
 * escrita usa bloqueio otimista pela coluna {@code version}: grava só se a versão lida ainda for a atual.
 */
public interface PositionStore {
    List<Position> findByUser(UUID userId);

    Optional<Position> find(UUID id, UUID userId);

    int countByUser(UUID userId);

    /** Cria a posição se o usuário ainda não tem uma para o mesmo ativo; falso (sem alterar nada) se já tem. */
    boolean insertIfAbsent(Position position);

    /**
     * Substitui quantidade, preço médio e nome, com a versão como condição. Devolve a posição gravada (versão + 1) ou vazio se a
     * versão mudou ou a posição não existe mais. Com duas escritas simultâneas sobre a mesma versão, exatamente uma vence.
     */
    Optional<Position> update(Position next, long expectedVersion);

    /** Remove a posição se a versão ainda for a esperada; falso se mudou ou não existe. */
    boolean delete(UUID id, UUID userId, long expectedVersion);
}
