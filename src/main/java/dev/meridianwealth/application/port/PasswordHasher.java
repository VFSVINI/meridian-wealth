package dev.meridianwealth.application.port;

/** Hash de senha com sal e custo ajustável (BCrypt). Nunca se guarda nem registra em log a senha em claro. */
public interface PasswordHasher {
    String hash(String password);

    boolean matches(String password, String hash);
}
