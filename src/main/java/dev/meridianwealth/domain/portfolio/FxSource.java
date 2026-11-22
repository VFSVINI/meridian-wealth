package dev.meridianwealth.domain.portfolio;

/** De onde veio o câmbio de uma operação em dólar. */
public enum FxSource {
    /** PTAX (venda) do Banco Central do último dia útil até a data da operação. */
    PTAX,
    /** Informado pelo usuário (por exemplo, o câmbio da nota de corretagem). */
    USER,
    /** Operação herdada do item #12, anterior ao livro: o câmbio da compra nunca foi registrado. */
    UNKNOWN
}
