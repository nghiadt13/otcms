package fu.fa26.sep490.g58.otcms_be.appointment.web;


import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves a public request IP without trusting forwarding headers from untrusted peers. */
@Component
public class TrustedProxyIpResolver {
    private final List<Cidr> trustedProxies;

    public TrustedProxyIpResolver(AppointmentBookingProperties properties) {
        this.trustedProxies = parseCidrs(properties.publicRateLimit().trustedProxyCidrs());
    }

    public String resolve(HttpServletRequest request) {
        byte[] remote = parseAddress(request.getRemoteAddr());
        if (remote == null) return invalidPeerKey(request.getRemoteAddr());
        if (!isTrusted(remote)) return canonical(remote);

        String forwarded = request.getHeader("Forwarded");
        List<byte[]> chain = parseForwarded(forwarded);
        if (chain == null) chain = parseXForwardedFor(request.getHeader("X-Forwarded-For"));
        if (chain == null || chain.isEmpty()) return canonical(remote);

        byte[] client = remote;
        for (int index = chain.size() - 1; index >= 0 && isTrusted(client); index--) {
            client = chain.get(index);
        }
        return canonical(client);
    }

    private List<byte[]> parseForwarded(String header) {
        if (header == null || header.isBlank()) return null;
        List<byte[]> chain = new ArrayList<>();
        for (String element : header.split(",")) {
            String address = null;
            for (String parameter : element.split(";")) {
                String item = parameter.trim();
                if (item.regionMatches(true, 0, "for=", 0, 4)) {
                    address = unquote(item.substring(4).trim());
                    break;
                }
            }
            byte[] parsed = parseAddress(stripPort(address));
            if (parsed == null) return null;
            chain.add(parsed);
        }
        return chain;
    }

    private List<byte[]> parseXForwardedFor(String header) {
        if (header == null || header.isBlank()) return null;
        List<byte[]> chain = new ArrayList<>();
        for (String value : header.split(",")) {
            byte[] parsed = parseAddress(stripPort(value.trim()));
            if (parsed == null) return null;
            chain.add(parsed);
        }
        return chain;
    }

    private String stripPort(String value) {
        if (value == null) return null;
        if (value.startsWith("[")) {
            int closing = value.indexOf(']');
            return closing > 0 ? value.substring(1, closing) : null;
        }
        long colons = value.chars().filter(character -> character == ':').count();
        if (colons == 1) {
            int colon = value.lastIndexOf(':');
            String port = value.substring(colon + 1);
            if (!port.isEmpty() && port.chars().allMatch(Character::isDigit)) return value.substring(0, colon);
        }
        return value;
    }

    private String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private byte[] parseAddress(String value) {
        if (value == null || value.isBlank() || value.indexOf('%') >= 0) return null;
        if (value.indexOf(':') < 0) {
            String[] parts = value.split("\\.", -1);
            if (parts.length != 4) return null;
            byte[] result = new byte[4];
            for (int index = 0; index < parts.length; index++) {
                if (parts[index].isEmpty() || !parts[index].chars().allMatch(Character::isDigit)) return null;
                try {
                    int octet = Integer.parseInt(parts[index]);
                    if (octet > 255) return null;
                    result[index] = (byte) octet;
                } catch (NumberFormatException exception) {
                    return null;
                }
            }
            return result;
        }
        if (!value.matches("[0-9a-fA-F:.]+")) return null;
        try {
            byte[] result = InetAddress.getByName(value).getAddress();
            if (result.length == 16 && isIpv4Mapped(result)) return java.util.Arrays.copyOfRange(result, 12, 16);
            return result.length == 16 ? result : null;
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean isIpv4Mapped(byte[] address) {
        for (int index = 0; index < 10; index++) if (address[index] != 0) return false;
        return (address[10] & 0xff) == 0xff && (address[11] & 0xff) == 0xff;
    }

    private List<Cidr> parseCidrs(String configured) {
        if (configured == null || configured.isBlank()) return List.of();
        List<Cidr> result = new ArrayList<>();
        for (String value : configured.split(",")) {
            String[] parts = value.trim().split("/", -1);
            byte[] address = parseAddress(parts[0]);
            if (address == null || parts.length > 2) {
                throw new IllegalArgumentException("Invalid appointment booking trusted proxy CIDR");
            }
            int bits = address.length * 8;
            if (parts.length == 2) {
                try {
                    bits = Integer.parseInt(parts[1]);
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException("Invalid appointment booking trusted proxy CIDR", exception);
                }
            }
            if (bits < 0 || bits > address.length * 8) {
                throw new IllegalArgumentException("Invalid appointment booking trusted proxy CIDR");
            }
            result.add(new Cidr(address, bits));
        }
        return List.copyOf(result);
    }

    private boolean isTrusted(byte[] address) {
        return trustedProxies.stream().anyMatch(cidr -> cidr.matches(address));
    }

    private String canonical(byte[] address) {
        try {
            return InetAddress.getByAddress(address).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (Exception impossible) {
            throw new IllegalStateException("Parsed IP address could not be formatted", impossible);
        }
    }

    private String invalidPeerKey(String remoteAddress) {
        if (remoteAddress == null || remoteAddress.isBlank()) return "invalid-peer";
        return "invalid-peer:" + remoteAddress.substring(0, Math.min(remoteAddress.length(), 128));
    }

        private static class Cidr {
    private final byte[] network;
    private final int prefixLength;
    public Cidr(byte[] network, int prefixLength) {
        this.network = network;
        this.prefixLength = prefixLength;
    }
    public byte[] getNetwork() { return network; }
    public byte[] network() { return network; }
    public int getPrefixLength() { return prefixLength; }
    public int prefixLength() { return prefixLength; }
boolean matches(byte[] candidate) {
            if (candidate.length != network.length) return false;
            int remaining = prefixLength;
            for (int index = 0; index < network.length && remaining > 0; index++) {
                int bits = Math.min(8, remaining);
                int mask = 0xff << (8 - bits);
                if ((network[index] & mask) != (candidate[index] & mask)) return false;
                remaining -= bits;
            }
            return true;
        }
}
}
