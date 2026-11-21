package dev.meridianwealth.application;

/** Situação dos dados de um ativo, sempre exibida ao usuário. */
public enum DataStatus {
    /** Cotação de provedor real (tempo real, atrasada ou fechamento, conforme o selo). */
    LIVE,
    /** Nenhum provedor respondeu: exibindo o último dado real gravado no banco. */
    STORED,
    /** Há dados reais, mas em quantidade insuficiente para o motor de timing. */
    INSUFFICIENT,
    /** Sem dado em tempo real e sem dado gravado. */
    UNAVAILABLE
}
