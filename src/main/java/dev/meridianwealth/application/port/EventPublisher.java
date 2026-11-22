package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.portfolio.PortfolioEvent;

/** Porta de saída: publica um fato da carteira já confirmado no livro. Quem assina nunca derruba a operação que o gerou. */
public interface EventPublisher {
    void publish(PortfolioEvent event);
}
