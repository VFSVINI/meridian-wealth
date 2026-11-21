package dev.meridianwealth.application.port;

import dev.meridianwealth.application.TickBatch;

/** Porta de saída do stream: um canal (WebSocket, SSE...) que entrega cada lote aos clientes conectados. */
public interface TickSink {
    /** Nome do canal exibido no status (ex.: "WebSocket", "SSE"). */
    String channel();

    void publish(TickBatch batch);

    int clients();
}
