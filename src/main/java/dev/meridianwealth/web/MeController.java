package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.application.privacy.DataSubjectService;
import dev.meridianwealth.domain.suitability.InvestorProfile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    public record EraseRequest(String confirmEmail) {}

    private final AuthService auth;
    private final DataSubjectService dataSubject;

    public MeController(AuthService auth, DataSubjectService dataSubject) {
        this.auth = auth;
        this.dataSubject = dataSubject;
    }

    /** Acesso e portabilidade (LGPD, art. 18): tudo que a plataforma guarda do próprio usuário, como arquivo JSON para baixar. */
    @GetMapping("/export")
    public ResponseEntity<DataSubjectService.Export> export(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"meridianwealth-meus-dados.json\"").body(dataSubject.export(who));
    }

    /** Eliminação (LGPD, art. 18, VI): apaga a conta e tudo que ela possui; exige o e-mail da conta digitado. Sem volta. */
    @PostMapping("/erase")
    public DataSubjectService.Erasure erase(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestBody EraseRequest r) {
        return dataSubject.erase(who, r.confirmEmail());
    }

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
