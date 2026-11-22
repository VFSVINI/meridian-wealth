package dev.meridianwealth.domain.portfolio;

import dev.meridianwealth.domain.AssetType;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Identifica o ativo de uma posição. Ativo do universo do painel tem {@code symbol} (a chave é o símbolo); ativo sem cotação
 * (Tesouro, LCI, CRI, fundos...) é identificado pelo tipo e por um rótulo do usuário, para que duas LCIs de bancos diferentes
 * sejam posições diferentes. A chave é única por usuário.
 */
public record AssetRef(String symbol, AssetType type, String label) {
    public static final int MAX_LABEL = 120;

    public AssetRef {
        Objects.requireNonNull(type, "type");
        if (!type.investable()) throw new IllegalArgumentException(type.label() + " é só referência e não entra em carteira");
        symbol = symbol == null || symbol.isBlank() ? null : symbol.trim().toUpperCase(Locale.ROOT);
        label = label == null || label.isBlank() ? type.label() : label.strip();
        if (label.length() > MAX_LABEL) throw new IllegalArgumentException("O nome do ativo pode ter até " + MAX_LABEL + " caracteres");
        if (label.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("O nome do ativo tem caracteres inválidos");
    }

    /** Chave de unicidade: o símbolo, ou {@code T:TIPO:rotulo-normalizado}. */
    public String key() { return symbol != null ? symbol : "T:" + type.name() + ":" + slug(label); }

    static String slug(String text) {
        String n = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        String s = n.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return s.isEmpty() ? "sem-nome" : s.length() > 60 ? s.substring(0, 60) : s;
    }
}
