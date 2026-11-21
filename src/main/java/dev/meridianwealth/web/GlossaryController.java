package dev.meridianwealth.web;

import dev.meridianwealth.domain.glossary.Glossary;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Glossário didático das métricas do painel: o que é, como ler e quando se preocupar. Conteúdo educacional. */
@RestController
@RequestMapping("/api/glossary")
public class GlossaryController {
    static final String DISCLAIMER = "Conteúdo educacional. Não é recomendação de investimento.";

    public record EntryView(String id, String group, String groupLabel, String term, String what, String howToRead, String whenToWorry,
                            List<String> related) {
        static EntryView of(Glossary.Entry e) {
            return new EntryView(e.id(), e.group().name(), e.group().label(), e.term(), e.what(), e.howToRead(), e.whenToWorry(), e.related());
        }
    }

    public record GlossaryView(List<EntryView> entries, String disclaimer) {}

    static class UnknownEntry extends RuntimeException {
        UnknownEntry(String id) { super("Sem entrada no glossário: " + id); }
    }

    @GetMapping
    public GlossaryView all() { return new GlossaryView(Glossary.all().stream().map(EntryView::of).toList(), DISCLAIMER); }

    @GetMapping("/{id}")
    public EntryView one(@PathVariable String id) {
        return Glossary.find(id).map(EntryView::of).orElseThrow(() -> new UnknownEntry(id));
    }

    @RestControllerAdvice(assignableTypes = GlossaryController.class)
    static class Errors {
        @ExceptionHandler(UnknownEntry.class)
        @ResponseStatus(HttpStatus.NOT_FOUND)
        Map<String, String> unknown(UnknownEntry e) { return Map.of("error", e.getMessage()); }
    }
}
