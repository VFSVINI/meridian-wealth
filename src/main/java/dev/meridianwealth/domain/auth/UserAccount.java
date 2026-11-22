package dev.meridianwealth.domain.auth;

import dev.meridianwealth.domain.suitability.InvestorProfile;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Conta de usuário. {@code passwordHash} só existe para {@link AuthProvider#PASSWORD} e {@code googleSubject} (o {@code sub} do
 * Google, estável mesmo que o e-mail mude) só para {@link AuthProvider#GOOGLE}. {@code profile} é o perfil de investidor
 * salvo pelo próprio usuário; nulo até ele passar pelo suitability.
 */
public record UserAccount(UUID id, String email, String displayName, String avatarUrl, AuthProvider provider, String passwordHash,
                          String googleSubject, Instant createdAt, InvestorProfile profile) {
    public UserAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(createdAt, "createdAt");
        if (provider == AuthProvider.PASSWORD && (passwordHash == null || passwordHash.isBlank())) {
            throw new IllegalArgumentException("Conta com senha precisa do hash");
        }
        if (provider == AuthProvider.GOOGLE && (googleSubject == null || googleSubject.isBlank())) {
            throw new IllegalArgumentException("Conta Google precisa do sub");
        }
        if (provider != AuthProvider.PASSWORD && passwordHash != null) throw new IllegalArgumentException("Só conta com senha tem hash");
        displayName = displayName == null || displayName.isBlank() ? email.substring(0, email.indexOf('@')) : displayName.strip();
        if (displayName.length() > 80) displayName = displayName.substring(0, 80);
    }

    public UserAccount withProfile(InvestorProfile newProfile) {
        return new UserAccount(id, email, displayName, avatarUrl, provider, passwordHash, googleSubject, createdAt, newProfile);
    }

    public boolean demo() { return provider == AuthProvider.DEMO; }
}
