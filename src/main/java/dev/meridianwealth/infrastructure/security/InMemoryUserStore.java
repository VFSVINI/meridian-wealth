package dev.meridianwealth.infrastructure.security;

import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.domain.auth.AuthProvider;
import dev.meridianwealth.domain.auth.UserAccount;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Contas em memória: somem quando a aplicação reinicia. Enquanto não existe a migração de usuários (item #12), é o que há; a
 * porta {@link UserStore} é a mesma que o adaptador JDBC vai implementar. Todas as operações compartilham um monitor, então a
 * unicidade de e-mail vale sob concorrência.
 */
public class InMemoryUserStore implements UserStore {
    private final Map<UUID, UserAccount> byId = new HashMap<>();
    private final Map<String, UUID> byEmail = new HashMap<>();
    private final Map<String, UUID> byGoogleSubject = new HashMap<>();

    @Override
    public synchronized Optional<UserAccount> findById(UUID id) { return Optional.ofNullable(byId.get(id)); }

    @Override
    public synchronized Optional<UserAccount> findByEmail(String normalizedEmail) {
        return Optional.ofNullable(byEmail.get(normalizedEmail)).map(byId::get);
    }

    @Override
    public synchronized Optional<UserAccount> findByGoogleSubject(String subject) {
        return Optional.ofNullable(byGoogleSubject.get(subject)).map(byId::get);
    }

    @Override
    public synchronized boolean createIfAbsent(UserAccount account) {
        if (byEmail.containsKey(account.email()) || byId.containsKey(account.id())) return false;
        if (account.googleSubject() != null && byGoogleSubject.containsKey(account.googleSubject())) return false;
        byId.put(account.id(), account);
        byEmail.put(account.email(), account.id());
        if (account.googleSubject() != null) byGoogleSubject.put(account.googleSubject(), account.id());
        return true;
    }

    @Override
    public synchronized void update(UserAccount account) {
        UserAccount old = byId.get(account.id());
        if (old == null) throw new IllegalArgumentException("Conta inexistente");
        if (!old.email().equals(account.email())) throw new IllegalArgumentException("O e-mail não muda por aqui");
        byId.put(account.id(), account);
    }

    @Override
    public synchronized void delete(UUID id) {
        UserAccount u = byId.remove(id);
        if (u == null) return;
        byEmail.remove(u.email());
        if (u.googleSubject() != null) byGoogleSubject.remove(u.googleSubject());
    }

    @Override
    public synchronized int countPermanent() { return (int) byId.values().stream().filter(u -> u.provider() != AuthProvider.DEMO).count(); }

    @Override
    public synchronized int countDemo() { return (int) byId.values().stream().filter(UserAccount::demo).count(); }

    @Override
    public synchronized int purgeDemoBefore(Instant cutoff) {
        var expired = byId.values().stream().filter(u -> u.demo() && u.createdAt().isBefore(cutoff)).toList();
        for (UserAccount u : expired) {
            byId.remove(u.id());
            byEmail.remove(u.email());
        }
        return expired.size();
    }
}
