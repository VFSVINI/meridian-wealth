package dev.meridianwealth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Regras executáveis do ADR 001 (hexagonal estrita) e do ADR 004 (dados 100% reais).
 * Uma violação quebra o {@code mvn verify}.
 */
@AnalyzeClasses(packages = "dev.meridianwealth", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    private static final String DOMAIN = "dev.meridianwealth.domain..";
    private static final String APPLICATION = "dev.meridianwealth.application..";
    private static final String INFRASTRUCTURE = "dev.meridianwealth.infrastructure..";
    private static final String WEB = "dev.meridianwealth.web..";
    private static final String CONFIG = "dev.meridianwealth.config..";

    @ArchTest
    static final ArchRule domainDependsOnlyOnTheJdk = classes().that().resideInAPackage(DOMAIN)
            .should().onlyDependOnClassesThat().resideInAnyPackage(DOMAIN, "java..")
            .because("o domínio é puro: só JDK e o próprio domínio (ADR 001)");

    @ArchTest
    static final ArchRule applicationDependsOnlyOnDomainAndJdk = classes().that().resideInAPackage(APPLICATION)
            .should().onlyDependOnClassesThat().resideInAnyPackage(APPLICATION, DOMAIN, "java..")
            .because("casos de uso são POJOs sem Spring, Jackson ou adaptadores (ADR 001)");

    @ArchTest
    static final ArchRule webDoesNotTouchInfrastructure = noClasses().that().resideInAPackage(WEB)
            .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, CONFIG)
            .because("adaptadores de entrada falam com a aplicação por portas (ADR 001)");

    @ArchTest
    static final ArchRule infrastructureDoesNotTouchWeb = noClasses().that().resideInAPackage(INFRASTRUCTURE)
            .should().dependOnClassesThat().resideInAnyPackage(WEB, CONFIG)
            .because("adaptadores de saída não conhecem a borda de entrada (ADR 001)");

    @ArchTest
    static final ArchRule noSimulatorInProduction = noClasses()
            .should().haveSimpleNameContaining("Simulat")
            .orShould().haveSimpleNameContaining("Synthetic")
            .orShould().haveSimpleNameContaining("Fake")
            .because("dados sintéticos só existem em src/test (ADR 004)");

    @ArchTest
    static final ArchRule randomnessOnlyForRetryJitter = noClasses().that()
            .resideOutsideOfPackage("dev.meridianwealth.infrastructure.resilience..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.Random")
            .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.ThreadLocalRandom")
            .because("aleatoriedade sem semente só no jitter do retry; nunca para gerar preços (ADR 004)");

    @ArchTest
    static final ArchRule seededRandomnessOnlyForRetryJitterAndMonteCarlo = noClasses().that()
            .resideOutsideOfPackages("dev.meridianwealth.infrastructure.resilience..", "dev.meridianwealth.domain.montecarlo..")
            .should().dependOnClassesThat().haveFullyQualifiedName("java.util.SplittableRandom")
            .because("o único sorteio com semente explícita fora do jitter é o do Monte Carlo, que gera cenários rotulados e nunca preços (ADR 020)");

    @ArchTest
    static final ArchRule monteCarloIsPureDomain = classes().that().resideInAPackage("dev.meridianwealth.domain.montecarlo..")
            .should().onlyDependOnClassesThat().resideInAnyPackage("dev.meridianwealth.domain.montecarlo..", "java..")
            .because("o motor de projeção é puro e reprodutível: só JDK e ele mesmo (ADR 020)");
}
