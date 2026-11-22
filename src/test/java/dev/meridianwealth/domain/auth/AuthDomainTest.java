package dev.meridianwealth.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.meridianwealth.domain.suitability.InvestorProfile;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthDomainTest {
    @Test void emailIsTrimmedLowercasedAndValidated() {
        assertThat(Email.normalize("  Ana.Silva@Exemplo.COM ")).isEqualTo("ana.silva@exemplo.com");
        for (String bad : new String[] {null, "", "  ", "sem-arroba", "a@b", "a@@b.com", "a b@c.com", "a@b c.com", "a@b.c", "@b.com", "a@.com"}) {
            assertThatThrownBy(() -> Email.normalize(bad)).as("deveria recusar: " + bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> Email.normalize("a".repeat(250) + "@b.com")).hasMessageContaining("inválido");
        assertThatThrownBy(() -> Email.normalize("a\u0000b@c.com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void passwordPolicyRequiresLengthAndRefusesObviousPasswords() {
        assertThat(PasswordPolicy.violation("uma frase longa e boa", "ana@exemplo.com")).isNull();
        assertThat(PasswordPolicy.violation("curta123", null)).contains("pelo menos 12");
        assertThat(PasswordPolicy.violation("aaaaaaaaaaaaaaaa", null)).contains("repetitiva");
        assertThat(PasswordPolicy.violation("123456789012", null)).contains("muito comum");
        assertThat(PasswordPolicy.violation("PassWord1234", null)).contains("muito comum");
        assertThat(PasswordPolicy.violation(null, null)).contains("Informe");
        assertThat(PasswordPolicy.violation("            ", null)).contains("Informe");
    }

    @Test void passwordPolicyRefusesPasswordsThatContainTheEmail() {
        assertThat(PasswordPolicy.violation("maria.souza@exemplo.com", "maria.souza@exemplo.com")).contains("e-mail");
        assertThat(PasswordPolicy.violation("ilovemaria.souza2026", "maria.souza@exemplo.com")).contains("e-mail");
        assertThat(PasswordPolicy.violation("ana-gosta-de-cafe-forte", "ana@exemplo.com")).isNull();
    }

    @Test void passwordPolicyLimitIsInBytesBecauseBcryptIgnoresTheRest() {
        String ok72 = "a1b2".repeat(18);
        assertThat(ok72.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
        assertThat(PasswordPolicy.violation(ok72, null)).isNull();
        assertThat(PasswordPolicy.violation(ok72 + "x", null)).contains("72 bytes");
        // 45 caracteres acentuados ocupam 75 bytes em UTF-8
        assertThat(PasswordPolicy.violation("ção".repeat(15), null)).contains("72 bytes");
    }

    @Test void accountRequiresTheRightCredentialForItsProvider() {
        Instant t = Instant.parse("2026-11-21T12:00:00Z");
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new UserAccount(id, "a@b.com", "A", null, AuthProvider.PASSWORD, null, null, t, null)).hasMessageContaining("hash");
        assertThatThrownBy(() -> new UserAccount(id, "a@b.com", "A", null, AuthProvider.GOOGLE, null, null, t, null)).hasMessageContaining("sub");
        assertThatThrownBy(() -> new UserAccount(id, "a@b.com", "A", null, AuthProvider.GOOGLE, "hash", "sub", t, null)).hasMessageContaining("hash");
        UserAccount demo = new UserAccount(id, "demo@demo.invalid", "  ", null, AuthProvider.DEMO, null, null, t, null);
        assertThat(demo.demo()).isTrue();
        assertThat(demo.displayName()).isEqualTo("demo");
        UserAccount withProfile = demo.withProfile(InvestorProfile.MODERATE);
        assertThat(withProfile.profile()).isEqualTo(InvestorProfile.MODERATE);
        assertThat(demo.profile()).isNull();
        assertThat(new UserAccount(id, "a@b.com", "x".repeat(200), null, AuthProvider.PASSWORD, "h", null, t, null).displayName()).hasSize(80);
    }
}
