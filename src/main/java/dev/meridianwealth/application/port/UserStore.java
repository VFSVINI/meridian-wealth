package dev.meridianwealth.application.port;

import dev.meridianwealth.domain.auth.UserAccount;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Armazenamento de contas. A criação é atômica: dois cadastros simultâneos com o mesmo e-mail nunca criam duas contas. */
public interface UserStore {
    Optional<UserAccount> findById(UUID id);

    Optional<UserAccount> findByEmail(String normalizedEmail);

    Optional<UserAccount> findByGoogleSubject(String subject);

    /** Cria a conta se o e-mail ainda não existir; devolve falso (sem alterar nada) se já existir. */
    boolean createIfAbsent(UserAccount account);

    /** Substitui a conta existente (por exemplo, para salvar o perfil). */
    void update(UserAccount account);

    /** Quantidade de contas permanentes (sem as de demonstração), para o limite de capacidade. */
    int countPermanent();

    /** Remove as contas de demonstração criadas antes de {@code cutoff}; devolve quantas saíram. */
    int purgeDemoBefore(Instant cutoff);

    int countDemo();
}
