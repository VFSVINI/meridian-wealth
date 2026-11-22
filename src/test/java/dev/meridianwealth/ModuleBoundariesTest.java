package dev.meridianwealth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.dependencies.Slice;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fronteiras entre os módulos de negócio (ADR 016): o monólito é modular e cada módulo só enxerga os módulos que declara abaixo. Uma
 * dependência nova entre módulos precisa ser decidida e escrita aqui, não aparecer por acidente. É isso que mantém a opção de extrair um
 * módulo para serviço próprio no dia em que houver motivo (ver {@code docs/decisoes/016-monolito-modular.md}).
 */
class ModuleBoundariesTest {
    private static final String ROOT = "dev.meridianwealth.";
    private static final JavaClasses CLASSES = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests()).importPackages("dev.meridianwealth");

    /** Módulos do domínio e, para cada um, os outros módulos de que pode depender. */
    private static final Map<String, Set<String>> DOMAIN = Map.of(
            "auth", Set.of("suitability"),
            "composition", Set.of(),
            "glossary", Set.of(),
            "impact", Set.of("suitability"),
            "portfolio", Set.of(),
            "reports", Set.of("composition"),
            "suitability", Set.of(),
            "thesis", Set.of());

    /** Módulos da camada de aplicação com subpacote próprio. */
    private static final Map<String, Set<String>> APPLICATION = Map.of(
            "auth", Set.of(),
            "portfolio", Set.of("auth"),
            "privacy", Set.of("auth"));

    private static void check(String layer, Map<String, Set<String>> modules) {
        for (Map.Entry<String, Set<String>> m : modules.entrySet()) {
            List<String> forbidden = modules.keySet().stream().filter(o -> !o.equals(m.getKey()) && !m.getValue().contains(o)).map(o -> ROOT + layer + "." + o + "..").toList();
            if (forbidden.isEmpty()) continue;
            noClasses().that().resideInAPackage(ROOT + layer + "." + m.getKey() + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                    .as("módulo " + layer + "." + m.getKey() + " só pode depender de " + (m.getValue().isEmpty() ? "ninguém" : m.getValue()))
                    .check(CLASSES);
        }
    }

    @Test void domainModulesOnlyDependOnWhatTheyDeclare() { check("domain", DOMAIN); }

    @Test void applicationModulesOnlyDependOnWhatTheyDeclare() { check("application", APPLICATION); }

    @Test void theSharedKernelNeverDependsOnABusinessModule() {
        noClasses().that().resideInAPackage(ROOT + "domain")
                .should().dependOnClassesThat().resideInAnyPackage(DOMAIN.keySet().stream().map(m -> ROOT + "domain." + m + "..").toArray(String[]::new))
                .because("o núcleo compartilhado (candle, indicador, sinal) vem antes dos módulos, nunca depende deles")
                .check(CLASSES);
    }

    @Test void theCoreServicesDoNotDependOnAuthOrPortfolio() {
        noClasses().that().resideInAPackage(ROOT + "application")
                .should().dependOnClassesThat().resideInAnyPackage(ROOT + "application.portfolio..", ROOT + "domain.portfolio..")
                .because("mercado, composição, relatórios e teses não conhecem a carteira do usuário")
                .check(CLASSES);
    }

    @Test void thereAreNoCyclesBetweenModules() {
        slices().matching(ROOT + "domain.(*)..").should().beFreeOfCycles().check(CLASSES);
        // "port" é o contrato compartilhado dos módulos (as portas citam tipos deles); o ciclo que importa é entre os módulos em si
        slices().matching(ROOT + "application.(*)..")
                .that(DescribedPredicate.describe("são módulos de negócio", (Slice s) -> !s.getDescription().endsWith("port")))
                .should().beFreeOfCycles().check(CLASSES);
    }
}
