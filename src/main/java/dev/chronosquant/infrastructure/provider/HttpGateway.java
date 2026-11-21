package dev.chronosquant.infrastructure.provider;

import java.io.IOException;
import java.util.Map;

/** Porta HTTP mínima, substituível em testes. */
public interface HttpGateway {
    String get(String url, Map<String, String> headers) throws IOException;
}
