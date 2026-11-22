package dev.meridianwealth.application.risk;

/** Todas as vagas de simulação estão ocupadas: a requisição foi recusada para não empilhar CPU. Tentar de novo em instantes resolve. */
public class MonteCarloBusyException extends RuntimeException {
    public MonteCarloBusyException() { super("Muitas projeções ao mesmo tempo. Tente de novo em alguns segundos."); }
}
