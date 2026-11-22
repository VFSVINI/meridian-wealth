package dev.meridianwealth.infrastructure.security;

import dev.meridianwealth.application.port.RefreshTokenStore;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh tokens em memória, com rotação e detecção de reuso. Todas as operações compartilham um monitor, o que torna a troca
 * atômica: com dois pedidos simultâneos para o mesmo token, o primeiro a entrar o consome e o segundo o encontra "já usado",
 * o que revoga a família inteira (inclusive o token novo que o primeiro acabou de receber).
 */
public class InMemoryRefreshTokenStore implements RefreshTokenStore {
    private static final int PURGE_THRESHOLD = 50_000;

    private static final class Slot {
        final Entry entry;
        boolean used;

        Slot(Entry entry) { this.entry = entry; }
    }

    private final Map<String, Slot> byHash = new HashMap<>();
    private final Map<String, Set<String>> hashesByFamily = new HashMap<>();

    @Override
    public synchronized void save(Entry entry) {
        if (byHash.size() > PURGE_THRESHOLD) purgeExpired(Instant.now());
        put(entry);
    }

    @Override
    public synchronized Rotation rotate(String presentedHash, String nextHash, Instant nextExpiresAt, Instant now) {
        Slot slot = byHash.get(presentedHash);
        if (slot == null) return new Rotation.Invalid();
        Entry previous = slot.entry;
        if (!previous.expiresAt().isAfter(now)) {
            remove(presentedHash, previous.familyId());
            return new Rotation.Invalid();
        }
        if (slot.used) {
            revokeFamily(previous.familyId());
            return new Rotation.Reused(previous.familyId(), previous.userId());
        }
        slot.used = true;
        put(new Entry(nextHash, previous.familyId(), previous.userId(), nextExpiresAt));
        return new Rotation.Rotated(previous);
    }

    @Override
    public synchronized void revokeFamilyOf(String presentedHash) {
        Slot slot = byHash.get(presentedHash);
        if (slot != null) revokeFamily(slot.entry.familyId());
    }

    @Override
    public synchronized void revokeAllOf(UUID userId) {
        Set<String> families = new HashSet<>();
        byHash.values().stream().filter(s -> s.entry.userId().equals(userId)).forEach(s -> families.add(s.entry.familyId()));
        families.forEach(this::revokeFamily);
    }

    private void put(Entry e) {
        byHash.put(e.hash(), new Slot(e));
        hashesByFamily.computeIfAbsent(e.familyId(), k -> new HashSet<>()).add(e.hash());
    }

    private void remove(String hash, String family) {
        byHash.remove(hash);
        Set<String> set = hashesByFamily.get(family);
        if (set != null) {
            set.remove(hash);
            if (set.isEmpty()) hashesByFamily.remove(family);
        }
    }

    private void revokeFamily(String family) {
        Set<String> hashes = hashesByFamily.remove(family);
        if (hashes != null) hashes.forEach(byHash::remove);
    }

    private void purgeExpired(Instant now) {
        var expired = byHash.values().stream().filter(s -> !s.entry.expiresAt().isAfter(now)).map(s -> s.entry).toList();
        expired.forEach(e -> remove(e.hash(), e.familyId()));
    }
}
