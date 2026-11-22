package dev.meridianwealth.domain.auth;

/** Como a conta se autentica. {@code DEMO} é a sessão efêmera do modo demonstração (nunca vira conta permanente). */
public enum AuthProvider {
    PASSWORD,
    GOOGLE,
    DEMO
}
