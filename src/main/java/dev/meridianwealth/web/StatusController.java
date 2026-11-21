package dev.meridianwealth.web;

import dev.meridianwealth.application.StatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Status da ingestão: provedores (circuit breaker, rate limit), cache, persistência, canais e procedência. */
@RestController
@RequestMapping("/api")
public class StatusController {
    private final StatusService status;

    public StatusController(StatusService status) { this.status = status; }

    @GetMapping("/status")
    public StatusService.Status status() { return status.status(); }
}
