package dev.meridianwealth.domain.portfolio;

/** Tipos de entrada do livro de movimentações. */
public enum EntryType {
    /** Posição aberta (saldo inicial informado ou importado): define quantidade e preço médio. */
    OPENING,
    /** Compra: aumenta a quantidade e recalcula o preço médio ponderado. */
    BUY,
    /** Venda: reduz a quantidade, mantém o preço médio e realiza resultado. */
    SELL,
    /** Ajuste manual: substitui quantidade e preço médio pelo que o usuário declara. */
    ADJUSTMENT,
    /** Baixa: a posição é removida sem venda (sem resultado realizado). */
    WRITE_OFF
}
