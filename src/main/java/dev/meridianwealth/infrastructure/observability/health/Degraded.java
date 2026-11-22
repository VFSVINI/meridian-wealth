package dev.meridianwealth.infrastructure.observability.health;

import org.springframework.boot.actuate.health.Status;

/** Estado "degradado": a aplicação funciona, mas alguma dependência de dados está faltando ou desatualizada. Responde HTTP 200. */
final class Degraded {
    static final Status STATUS = new Status("DEGRADED", "Funcionando com dado faltando ou desatualizado");

    private Degraded() {}
}
