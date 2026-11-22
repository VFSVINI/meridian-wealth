package dev.meridianwealth.infrastructure.events;

import dev.meridianwealth.application.port.EventPublisher;
import dev.meridianwealth.domain.portfolio.PortfolioEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Barramento de eventos em memória, síncrono e dentro do processo. Cada assinante roda isolado: se um falhar, o erro é registrado e os
 * outros (e a operação que gerou o evento, que já foi gravada no livro) seguem. É a costura onde um outbox ou um broker entram depois,
 * sem mudar quem publica.
 */
public class InProcessEventBus implements EventPublisher {
    private static final Logger LOG = Logger.getLogger(InProcessEventBus.class.getName());
    private final List<Consumer<PortfolioEvent>> subscribers = new CopyOnWriteArrayList<>();

    public void subscribe(Consumer<PortfolioEvent> subscriber) { subscribers.add(subscriber); }

    @Override
    public void publish(PortfolioEvent event) {
        for (Consumer<PortfolioEvent> s : subscribers) {
            try {
                s.accept(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Assinante de eventos falhou em {0}: {1}", new Object[] {event.type(), e.toString()});
            }
        }
    }
}
