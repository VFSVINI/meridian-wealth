package dev.meridianwealth.infrastructure.resilience;

/** Erro 4xx do provedor (ex.: símbolo desconhecido). Não é repetido e não abre o circuito. */
public class ClientErrorException extends RuntimeException {
    public ClientErrorException(String message) { super(message); }
}
