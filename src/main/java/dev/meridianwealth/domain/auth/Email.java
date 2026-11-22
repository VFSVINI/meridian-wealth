package dev.meridianwealth.domain.auth;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Endereço de e-mail normalizado: sem espaços nas pontas e em minúsculas, para que "Ana@x.com" e "ana@x.com " sejam a mesma
 * conta. A validação é propositalmente simples (uma única arroba, domínio com ponto, até 254 caracteres, sem espaço nem
 * caractere de controle): quem confirma o endereço de verdade é o Google ({@code email_verified}) ou, no futuro, um link de
 * confirmação.
 */
public final class Email {
    public static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s\\p{Cntrl}]{1,64}@[^@\\s\\p{Cntrl}]+\\.[^@\\s\\p{Cntrl}.]{2,}$");

    private Email() {}

    /** Normaliza e valida; lança {@link IllegalArgumentException} com mensagem em português se for inválido. */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("Informe o e-mail");
        String e = raw.trim().toLowerCase(Locale.ROOT);
        if (e.length() > MAX_LENGTH || !SHAPE.matcher(e).matches()) throw new IllegalArgumentException("E-mail inválido");
        return e;
    }
}
