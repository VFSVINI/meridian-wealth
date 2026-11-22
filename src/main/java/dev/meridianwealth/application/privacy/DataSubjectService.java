package dev.meridianwealth.application.privacy;

import dev.meridianwealth.application.auth.AuthException;
import dev.meridianwealth.application.auth.AuthException.Code;
import dev.meridianwealth.application.auth.AuthPrincipal;
import dev.meridianwealth.application.auth.AuthService;
import dev.meridianwealth.application.port.PortfolioLedger;
import dev.meridianwealth.application.port.PositionStore;
import dev.meridianwealth.application.port.RefreshTokenStore;
import dev.meridianwealth.application.port.UserStore;
import dev.meridianwealth.domain.portfolio.LedgerEntry;
import dev.meridianwealth.domain.portfolio.Position;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Direitos do titular dos dados (LGPD, art. 18): acesso e portabilidade ({@link #export}) e eliminação ({@link #erase}). O dono vem
 * sempre do token, nunca de parâmetro, então ninguém exporta nem apaga a conta de outra pessoa. A exportação traz tudo que a plataforma
 * guarda da pessoa e <b>nunca</b> o hash da senha, tokens ou identificadores internos de sessão. A eliminação apaga a conta, as
 * posições, o livro de movimentações e as sessões; só a contagem do que saiu é registrada em log, nunca o conteúdo.
 */
public final class DataSubjectService {
    private static final System.Logger LOG = System.getLogger(DataSubjectService.class.getName());

    /** Tudo que a plataforma guarda da pessoa, em formato legível por máquina. */
    public record Export(Instant generatedAt, AuthService.UserView account, List<Position> positions, List<LedgerEntry> ledger, List<String> notes) {}

    /** O que foi eliminado (só contagens). */
    public record Erasure(Instant erasedAt, int positions, int ledgerEntries) {}

    private final AuthService auth;
    private final UserStore users;
    private final RefreshTokenStore refreshTokens;
    private final PositionStore positions;
    private final PortfolioLedger ledger;
    private final Clock clock;

    public DataSubjectService(AuthService auth, UserStore users, RefreshTokenStore refreshTokens, PositionStore positions, PortfolioLedger ledger, Clock clock) {
        this.auth = auth;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.positions = positions;
        this.ledger = ledger;
        this.clock = clock;
    }

    public Export export(AuthPrincipal who) {
        AuthService.UserView account = auth.me(who);
        return new Export(clock.instant(), account, positions.findByUser(who.userId()), ledger.all(who.userId()),
                List.of("Exportação dos dados pessoais guardados pelo MeridianWealth (LGPD, art. 18, II e V).",
                        "Não inclui a senha (guardamos só um hash irreversível), tokens de sessão nem dados de mercado públicos.",
                        "Valores de posições e do livro estão na moeda de cada ativo, exatamente como foram gravados."));
    }

    /**
     * Elimina a conta e tudo que ela possui. Exige que a pessoa digite o próprio e-mail (contra o clique acidental e o uso de uma sessão
     * esquecida aberta); a sessão de demonstração, que é descartável, não exige.
     */
    public Erasure erase(AuthPrincipal who, String confirmEmail) {
        AuthService.UserView account = auth.me(who);
        if (!account.demo() && (confirmEmail == null || !account.email().equalsIgnoreCase(confirmEmail.trim()))) {
            throw new AuthException(Code.INVALID_INPUT, "Para excluir a conta, digite o e-mail dela");
        }
        refreshTokens.revokeAllOf(who.userId());
        int entries = ledger.deleteAllOf(who.userId());
        int removedPositions = positions.deleteAllOf(who.userId());
        users.delete(who.userId());
        LOG.log(System.Logger.Level.INFO, "Conta eliminada a pedido do titular: {0} posições e {1} entradas do livro removidas", removedPositions, entries);
        return new Erasure(clock.instant(), removedPositions, entries);
    }
}
