package dev.meridianwealth.infrastructure.security;

import dev.meridianwealth.application.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** BCrypt com custo configurável (12 por padrão em produção; os testes usam um custo menor só para ficarem rápidos). */
public class BcryptPasswordHasher implements PasswordHasher {
    public static final int DEFAULT_STRENGTH = 12;
    private final BCryptPasswordEncoder encoder;

    public BcryptPasswordHasher(int strength) {
        if (strength < 4 || strength > 16) throw new IllegalArgumentException("Custo do BCrypt fora de 4 a 16");
        this.encoder = new BCryptPasswordEncoder(strength);
    }

    @Override
    public String hash(String password) { return encoder.encode(password); }

    @Override
    public boolean matches(String password, String hash) {
        try {
            return encoder.matches(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
