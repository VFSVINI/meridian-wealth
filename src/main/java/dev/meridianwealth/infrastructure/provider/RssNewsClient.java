package dev.meridianwealth.infrastructure.provider;

import dev.meridianwealth.application.ProviderStatus;
import dev.meridianwealth.application.port.ProviderStatusSource;
import dev.meridianwealth.infrastructure.resilience.ResilientExecutor;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

/** Manchetes de feeds RSS públicos (mundo e economia) para o índice de risco geopolítico. */
@Component
public class RssNewsClient implements ProviderStatusSource {
    private static final Logger log = LoggerFactory.getLogger(RssNewsClient.class);
    private static final Map<String, String> FEEDS = Map.of(
            "BBC World", "https://feeds.bbci.co.uk/news/world/rss.xml",
            "BBC Business", "https://feeds.bbci.co.uk/news/business/rss.xml",
            "Guardian World", "https://www.theguardian.com/world/rss");

    private final HttpGateway http;
    private final ResilientExecutor executor = ResilientExecutor.withDefaults("RSS", 4, 0.5);

    public RssNewsClient(HttpGateway http) { this.http = http; }

    public List<Headline> fetch() {
        List<Headline> out = new ArrayList<>();
        FEEDS.forEach((name, url) -> {
            try {
                out.addAll(executor.call(() -> parse(http.get(url, Map.of()), name)));
            } catch (RuntimeException e) {
                log.debug("Feed {} indisponível: {}", name, e.getMessage());
            }
        });
        return out;
    }

    static List<Headline> parse(String xml, String source) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList items = d.getElementsByTagName("item");
        List<Headline> out = new ArrayList<>();
        for (int i = 0; i < items.getLength() && out.size() < 15; i++) {
            org.w3c.dom.Element item = (org.w3c.dom.Element) items.item(i);
            NodeList titles = item.getElementsByTagName("title");
            if (titles.getLength() == 0) continue;
            out.add(new Headline(titles.item(0).getTextContent().strip(), source, text(item, "link"), time(text(item, "pubDate"))));
        }
        return out;
    }

    private static String text(org.w3c.dom.Element item, String tag) {
        NodeList n = item.getElementsByTagName(tag);
        return n.getLength() == 0 ? null : n.item(0).getTextContent().strip();
    }

    /** Horário do feed (RFC 822, como "Fri, 27 Nov 2026 10:00:00 GMT"); ilegível vira nulo, nunca um horário inventado. */
    static Instant time(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return null;
        try {
            return java.time.ZonedDateTime.parse(pubDate, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    @Override
    public ProviderStatus status() { return executor.stats(true); }
}
