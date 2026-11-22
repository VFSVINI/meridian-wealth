package dev.meridianwealth.infrastructure.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Descobre o IP real do cliente. O cabeçalho {@code X-Forwarded-For} é escrito por quem quiser, então só vale quando a conexão
 * vem de um proxy <b>confiável</b> (lista configurada). Nesse caso a cadeia é lida da direita para a esquerda, pulando os proxies
 * confiáveis: o primeiro endereço que não é de proxy confiável é o cliente. Um cliente que mande o próprio
 * {@code X-Forwarded-For} falso só consegue colocar endereços à esquerda do que o proxy confiável acrescentou, e eles são
 * ignorados. Sem proxy confiável configurado (o padrão), o cabeçalho é ignorado por completo e vale o endereço da conexão.
 *
 * <p>Só aceita literais de IP (v4 ou v6): nenhum nome é resolvido por DNS.
 */
public class ClientIpResolver {
    private static final Pattern IPV4 = Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:.]{2,45}$");

    private record Cidr(byte[] network, int bits) {
        boolean contains(byte[] addr) {
            if (addr.length != network.length) return false;
            int full = bits / 8;
            for (int i = 0; i < full; i++) if (addr[i] != network[i]) return false;
            int rem = bits % 8;
            if (rem == 0) return true;
            int mask = 0xFF << (8 - rem) & 0xFF;
            return (addr[full] & mask) == (network[full] & mask);
        }
    }

    private final List<Cidr> trusted;

    /** @param trustedProxies IPs ou faixas CIDR (por exemplo {@code 10.0.0.0/8}, {@code 172.18.0.5}, {@code ::1}) */
    public ClientIpResolver(List<String> trustedProxies) {
        List<Cidr> list = new ArrayList<>();
        for (String raw : trustedProxies == null ? List.<String>of() : trustedProxies) {
            String s = raw == null ? "" : raw.trim();
            if (s.isEmpty()) continue;
            int slash = s.indexOf('/');
            String host = slash < 0 ? s : s.substring(0, slash);
            byte[] bytes = literal(host);
            if (bytes == null) throw new IllegalArgumentException("Proxy confiável inválido (use IP ou CIDR): " + s);
            int bits = slash < 0 ? bytes.length * 8 : parseBits(s.substring(slash + 1), bytes.length * 8, s);
            list.add(new Cidr(bytes, bits));
        }
        this.trusted = List.copyOf(list);
    }

    /** A conexão original do cliente é HTTPS: direta, ou anunciada por um proxy confiável ({@code X-Forwarded-Proto}). */
    public boolean isHttps(jakarta.servlet.http.HttpServletRequest req) {
        if (req.isSecure()) return true;
        return "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto")) && isTrusted(req.getRemoteAddr());
    }

    public boolean isTrusted(String address) {
        byte[] b = literal(address);
        return b != null && trusted.stream().anyMatch(c -> c.contains(b));
    }

    /** @param remoteAddr endereço da conexão TCP; @param forwardedFor valor do cabeçalho X-Forwarded-For (pode ser nulo) */
    public String resolve(String remoteAddr, String forwardedFor) {
        if (forwardedFor == null || forwardedFor.isBlank() || !isTrusted(remoteAddr)) return remoteAddr;
        String[] parts = forwardedFor.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (literal(candidate) == null) return remoteAddr; // cadeia malformada: não confie em nada dela
            if (!isTrusted(candidate)) return candidate;
        }
        return remoteAddr;
    }

    private static int parseBits(String s, int max, String whole) {
        try {
            int bits = Integer.parseInt(s);
            if (bits < 0 || bits > max) throw new NumberFormatException();
            return bits;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Proxy confiável inválido (CIDR): " + whole);
        }
    }

    /** Bytes do IP se for um literal v4/v6; nulo caso contrário (nunca consulta DNS). */
    static byte[] literal(String s) {
        if (s == null) return null;
        String h = s.trim();
        if (h.startsWith("[") && h.endsWith("]")) h = h.substring(1, h.length() - 1);
        var m = IPV4.matcher(h);
        if (m.matches()) {
            for (int i = 1; i <= 4; i++) if (Integer.parseInt(m.group(i)) > 255) return null;
        } else if (!(h.indexOf(':') >= 0 && IPV6.matcher(h).matches())) {
            return null;
        }
        try {
            return InetAddress.getByName(h).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
