package dev.meridianwealth.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Países e regiões que o radar geopolítico reconhece numa manchete (item #51). A ligação é por palavras (nome do país, adjetivo, capital e líderes comuns nas
 * manchetes em inglês dos feeds): é um método simples e publicado, não um índice oficial. Uma manchete sem nenhum desses termos não liga a nenhum país, e um país
 * que nenhuma manchete cita fica "não informado" no mapa, nunca "risco zero". Os códigos são os ADM0_A3 do arquivo de fronteiras da Natural Earth (teste confere).
 */
public final class GeoRegions {
    private GeoRegions() {}

    public record Country(String iso3, String name, String region, List<Pattern> patterns) {}

    private static final List<Country> ALL = new ArrayList<>();

    private static void c(String iso3, String name, String region, String... terms) {
        List<Pattern> ps = new ArrayList<>();
        for (String t : terms) ps.add(Pattern.compile("(?<![a-z])" + Pattern.quote(t) + "(?![a-z])"));
        ALL.add(new Country(iso3, name, region, List.copyOf(ps)));
    }

    static {
        c("USA", "Estados Unidos", "Estados Unidos", "united states", "u.s.", "american", "washington", "white house", "trump", "pentagon", "federal reserve", "wall street");
        c("BRA", "Brasil", "Brasil", "brazil", "brazilian", "brasilia", "lula");
        c("CAN", "Canadá", "Américas", "canada", "canadian", "ottawa");
        c("MEX", "México", "Américas", "mexico", "mexican");
        c("ARG", "Argentina", "Américas", "argentina", "argentine", "milei");
        c("VEN", "Venezuela", "Américas", "venezuela", "venezuelan", "maduro", "caracas");
        c("COL", "Colômbia", "Américas", "colombia", "colombian", "bogota");
        c("CUB", "Cuba", "Américas", "cuba", "cuban", "havana");
        c("CHL", "Chile", "Américas", "chile", "chilean");
        c("PER", "Peru", "Américas", "peru", "peruvian");
        c("ECU", "Equador", "Américas", "ecuador", "ecuadorian");
        c("BOL", "Bolívia", "Américas", "bolivia", "bolivian");
        c("HTI", "Haiti", "Américas", "haiti", "haitian");
        c("GBR", "Reino Unido", "Europa", "britain", "british", "u.k.", "uk", "london", "england", "scotland");
        c("DEU", "Alemanha", "Europa", "germany", "german", "berlin");
        c("FRA", "França", "Europa", "france", "french", "paris", "macron");
        c("ITA", "Itália", "Europa", "italy", "italian", "rome");
        c("ESP", "Espanha", "Europa", "spain", "spanish", "madrid");
        c("NLD", "Países Baixos", "Europa", "netherlands", "dutch");
        c("GRC", "Grécia", "Europa", "greece", "greek", "athens");
        c("CHE", "Suíça", "Europa", "switzerland", "swiss");
        c("SWE", "Suécia", "Europa", "sweden", "swedish");
        c("NOR", "Noruega", "Europa", "norway", "norwegian");
        c("FIN", "Finlândia", "Europa", "finland", "finnish");
        c("HUN", "Hungria", "Europa", "hungary", "hungarian", "budapest");
        c("SRB", "Sérvia", "Europa", "serbia", "serbian", "belgrade");
        c("RUS", "Rússia", "Leste Europeu", "russia", "russian", "moscow", "kremlin", "putin");
        c("UKR", "Ucrânia", "Leste Europeu", "ukraine", "ukrainian", "kyiv", "kiev", "zelensky");
        c("POL", "Polônia", "Leste Europeu", "poland", "polish", "warsaw");
        c("BLR", "Belarus", "Leste Europeu", "belarus", "belarusian", "minsk");
        c("ROU", "Romênia", "Leste Europeu", "romania", "romanian");
        c("ISR", "Israel", "Oriente Médio", "israel", "israeli", "tel aviv", "netanyahu");
        c("PSX", "Palestina", "Oriente Médio", "gaza", "palestinian", "hamas", "west bank");
        c("IRN", "Irã", "Oriente Médio", "iran", "iranian", "tehran");
        c("IRQ", "Iraque", "Oriente Médio", "iraq", "iraqi", "baghdad");
        c("SYR", "Síria", "Oriente Médio", "syria", "syrian", "damascus");
        c("LBN", "Líbano", "Oriente Médio", "lebanon", "lebanese", "hezbollah", "beirut");
        c("YEM", "Iêmen", "Oriente Médio", "yemen", "yemeni", "houthi", "houthis", "red sea");
        c("SAU", "Arábia Saudita", "Oriente Médio", "saudi", "riyadh", "opec");
        c("ARE", "Emirados Árabes", "Oriente Médio", "emirates", "uae", "dubai", "abu dhabi");
        c("QAT", "Catar", "Oriente Médio", "qatar", "qatari", "doha");
        c("TUR", "Turquia", "Oriente Médio", "turkey", "turkish", "ankara", "erdogan");
        c("JOR", "Jordânia", "Oriente Médio", "jordan", "jordanian", "amman");
        c("EGY", "Egito", "Oriente Médio", "egypt", "egyptian", "cairo");
        c("CHN", "China", "Ásia-Pacífico", "china", "chinese", "beijing", "xi jinping");
        c("TWN", "Taiwan", "Ásia-Pacífico", "taiwan", "taiwanese", "taipei");
        c("JPN", "Japão", "Ásia-Pacífico", "japan", "japanese", "tokyo");
        c("KOR", "Coreia do Sul", "Ásia-Pacífico", "south korea", "south korean", "seoul");
        c("PRK", "Coreia do Norte", "Ásia-Pacífico", "north korea", "north korean", "pyongyang");
        c("IND", "Índia", "Ásia-Pacífico", "india", "indian", "delhi", "modi");
        c("PAK", "Paquistão", "Ásia-Pacífico", "pakistan", "pakistani", "islamabad");
        c("AFG", "Afeganistão", "Ásia-Pacífico", "afghanistan", "afghan", "taliban", "kabul");
        c("MMR", "Mianmar", "Ásia-Pacífico", "myanmar", "burma", "burmese");
        c("THA", "Tailândia", "Ásia-Pacífico", "thailand", "thai", "bangkok");
        c("VNM", "Vietnã", "Ásia-Pacífico", "vietnam", "vietnamese", "hanoi");
        c("PHL", "Filipinas", "Ásia-Pacífico", "philippines", "philippine", "filipino", "manila");
        c("IDN", "Indonésia", "Ásia-Pacífico", "indonesia", "indonesian", "jakarta");
        c("AUS", "Austrália", "Ásia-Pacífico", "australia", "australian", "canberra", "sydney");
        c("NZL", "Nova Zelândia", "Ásia-Pacífico", "new zealand");
        c("KAZ", "Cazaquistão", "Ásia-Pacífico", "kazakhstan", "kazakh");
        c("ZAF", "África do Sul", "África", "south africa", "south african", "pretoria", "johannesburg");
        c("NGA", "Nigéria", "África", "nigeria", "nigerian", "abuja", "lagos");
        c("ETH", "Etiópia", "África", "ethiopia", "ethiopian", "addis ababa");
        c("SDN", "Sudão", "África", "sudan", "sudanese", "khartoum");
        c("SDS", "Sudão do Sul", "África", "south sudan", "juba");
        c("LBY", "Líbia", "África", "libya", "libyan", "tripoli");
        c("SOM", "Somália", "África", "somalia", "somali", "mogadishu");
        c("COD", "Congo (RDC)", "África", "congo", "congolese", "kinshasa");
        c("KEN", "Quênia", "África", "kenya", "kenyan", "nairobi");
        c("MLI", "Mali", "África", "mali", "malian", "bamako");
        c("NER", "Níger", "África", "niger", "nigerien");
        c("DZA", "Argélia", "África", "algeria", "algerian", "algiers");
        c("MAR", "Marrocos", "África", "morocco", "moroccan", "rabat");
    }

    public static List<Country> all() { return List.copyOf(ALL); }

    /** Códigos (ADM0_A3) dos países que a manchete cita, sem repetir e na ordem da lista. */
    public static List<String> match(String title) {
        String t = title.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Country c : ALL) if (c.patterns().stream().anyMatch(p -> p.matcher(t).find())) out.add(c.iso3());
        // "south sudan" contém "sudan": só vale como Sudão se o título citar o Sudão em outro lugar
        if (t.contains("south sudan") && !t.replace("south sudan", "").contains("sudan")) out.remove("SDN");
        return out;
    }
}
