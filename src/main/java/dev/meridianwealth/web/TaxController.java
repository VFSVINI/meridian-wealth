package dev.meridianwealth.web;

import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.portfolio.PortfolioService;
import dev.meridianwealth.domain.portfolio.TaxReport;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Apoio ao imposto de renda a partir do livro de movimentações do próprio usuário. Não calcula imposto devido, não gera DARF e não
 * preenche a declaração: ver {@link TaxReport} e {@code docs/carteira.md} para o que é e o que não é apurado, com a base legal.
 */
@RestController
@RequestMapping("/api/me/tax")
public class TaxController {
    private final PortfolioService portfolio;

    public TaxController(PortfolioService portfolio) { this.portfolio = portfolio; }

    /** @param year ano-calendário; sem ele, o ano corrente */
    @GetMapping("/report")
    public TaxReport.Result report(@RequestAttribute(AuthPrincipal.REQUEST_ATTRIBUTE) AuthPrincipal who, @RequestParam(required = false) Integer year) {
        return portfolio.taxReport(who, year == null ? LocalDate.now().getYear() : year);
    }
}
