package dev.meridianwealth.application.risk;

/** A projeção não pode ser feita com dado real suficiente (nada é estimado para tapar a falta): a mensagem diz o que fazer. */
public class MonteCarloException extends RuntimeException {
    public MonteCarloException(String message) { super(message); }
}
