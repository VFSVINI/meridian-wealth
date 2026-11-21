package dev.meridianwealth.domain.thesis;

import dev.meridianwealth.domain.AssetType;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Tese institucional <b>genérica</b>: uma lente didática inspirada no estilo público e amplamente conhecido de uma gestora. Não
 * reproduz, não estima nem atribui nenhuma posição real a nenhuma instituição, e a caracterização é simplificada e não foi
 * revisada por ela. Cada tese traz premissas, as sensibilidades (o que tende a ser favorecido ou prejudicado SE a premissa se
 * confirmar) e o que a invalidaria.
 */
public record Thesis(String id, String name, String inspiration, String summary, List<String> premises, List<Sensitivity> sensitivities,
                     List<String> invalidations) {
    public Thesis {
        Objects.requireNonNull(id, "id");
        premises = List.copyOf(premises);
        sensitivities = List.copyOf(sensitivities);
        invalidations = List.copyOf(invalidations);
        if (premises.isEmpty() || sensitivities.isEmpty() || invalidations.isEmpty()) {
            throw new IllegalArgumentException("A tese " + id + " precisa de premissas, sensibilidades e invalidações");
        }
    }

    public enum Effect {
        FAVORECIDO_SE_CONFIRMAR("Tende a ser favorecido se a premissa se confirmar"),
        PREJUDICADO_SE_CONFIRMAR("Tende a ser prejudicado se a premissa se confirmar");

        private final String label;

        Effect(String label) { this.label = label; }

        public String label() { return label; }
    }

    /**
     * Grupo de ativos sensível à tese. Casa com uma posição quando o tipo está em {@code types} (se a lista não for vazia) E o
     * setor está em {@code sectors} (idem). As duas listas vazias nunca casam.
     */
    public record Sensitivity(String id, String label, Set<AssetType> types, Set<String> sectors, Effect effect, String rationale) {
        public Sensitivity {
            types = Set.copyOf(types);
            sectors = Set.copyOf(sectors);
            if (types.isEmpty() && sectors.isEmpty()) throw new IllegalArgumentException("Sensibilidade sem critério: " + id);
        }

        public boolean matches(Holding h) {
            return (types.isEmpty() || types.contains(h.type())) && (sectors.isEmpty() || (h.sector() != null && sectors.contains(h.sector())));
        }
    }
}
