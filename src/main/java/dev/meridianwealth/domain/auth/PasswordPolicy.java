package dev.meridianwealth.domain.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Política de senha no espírito do NIST SP 800-63B: tamanho mínimo em vez de regras de composição, recusa de senhas óbvias e
 * limite máximo. O máximo é de 72 <b>bytes</b> porque o BCrypt ignora o que passa disso: aceitar mais daria a falsa impressão de
 * que a senha inteira conta.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_BYTES = 72;
    private static final Set<String> COMMON = Set.of("123456789012", "1234567890123", "password1234", "passw0rd1234", "qwertyuiop12",
            "senha1234567", "senhasenha123", "meridianwealth", "meridianwealth1", "123123123123", "abcdefghijkl", "iloveyou1234");

    private PasswordPolicy() {}

    /** Devolve o motivo da recusa em português, ou nulo se a senha atende à política. */
    public static String violation(String password, String normalizedEmail) {
        if (password == null || password.isBlank()) return "Informe a senha";
        if (password.codePointCount(0, password.length()) < MIN_LENGTH) return "A senha precisa de pelo menos " + MIN_LENGTH + " caracteres";
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return "A senha pode ter no máximo " + MAX_BYTES + " bytes (cerca de " + MAX_BYTES + " caracteres comuns)";
        String lower = password.toLowerCase(Locale.ROOT);
        if (password.chars().distinct().count() < 4) return "A senha é repetitiva demais";
        if (COMMON.contains(lower)) return "Essa senha é muito comum";
        if (normalizedEmail != null) {
            String local = normalizedEmail.substring(0, Math.max(0, normalizedEmail.indexOf('@')));
            if (lower.equals(normalizedEmail) || (local.length() >= 6 && lower.contains(local))) return "A senha não pode conter o seu e-mail";
        }
        return null;
    }
}
