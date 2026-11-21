package dev.meridianwealth.domain.composition;

/**
 * Links diretos do Google Maps a partir de uma coordenada, no formato documentado em "Maps URLs"
 * (developers.google.com/maps/documentation/urls, lido em 2026-11-21): {@code map_action=map} com
 * {@code basemap=satellite} e {@code map_action=pano} com {@code viewpoint}.
 */
public final class MapLinks {
    private MapLinks() {}

    public static String satellite(Coordinates c) {
        return "https://www.google.com/maps/@?api=1&map_action=map&center=" + point(c) + "&zoom=17&basemap=satellite";
    }

    public static String streetView(Coordinates c) {
        return "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=" + point(c);
    }

    private static String point(Coordinates c) { return c.lat().toPlainString() + "%2C" + c.lng().toPlainString(); }
}
