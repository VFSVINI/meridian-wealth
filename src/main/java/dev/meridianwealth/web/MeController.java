package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dados do próprio usuário. Nenhuma rota recebe o identificador do dono: o {@link AuthPrincipal} vem do token já validado pelo
 * filtro de autenticação, então um usuário não tem como pedir os dados de outro. Rotas novas de dados pessoais entram sob
 * {@code /api/me}, que o filtro protege.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {
    public record ProfileRequest(InvestorProfile profile) {}

    public record ProfileView(InvestorProfile profile, boolean needsSuitability) {}

    private final AuthService auth;

    public MeController(AuthService auth) { this.auth = auth; }

    @GetMapping
    public AuthService.UserView me(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who) { return auth.me(who); }

    @GetMapping("/profile")
    public ProfileView profile(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who) {
        InvestorProfile p = auth.profile(who).orElse(null);
        return new ProfileView(p, p == null);
    }

    @PutMapping("/profile")
    public AuthService.UserView saveProfile(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestBody ProfileRequest r) {
        return auth.saveProfile(who, r.profile());
    }
}
