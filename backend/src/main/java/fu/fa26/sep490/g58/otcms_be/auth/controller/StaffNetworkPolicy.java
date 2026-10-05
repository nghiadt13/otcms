package fu.fa26.sep490.g58.otcms_be.auth.controller;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.auth.staff-network")
public class StaffNetworkPolicy {
    private List<String> allowedCidrs = List.of();
    private List<String> trustedProxyCidrs = List.of();
    private List<CidrRange> allowed = List.of();
    private List<CidrRange> trusted = List.of();
    public List<String> getAllowedCidrs() { return allowedCidrs; }
    public List<String> getTrustedProxyCidrs() { return trustedProxyCidrs; }
    public void setAllowedCidrs(List<String> value) { allowedCidrs = value == null ? List.of() : List.copyOf(value); allowed = parse(allowedCidrs); }
    public void setTrustedProxyCidrs(List<String> value) { trustedProxyCidrs = value == null ? List.of() : List.copyOf(value); trusted = parse(trustedProxyCidrs); }
    public boolean allows(String address) {
        try {
            byte[] candidate = InetAddress.getByName(address).getAddress();
            for (CidrRange range : allowed) if (range.matches(candidate)) return true;
        } catch (Exception ignored) { }
        return false;
    }
    public String clientAddress(String remoteAddress, String forwardedFor) {
        return clientAddress(remoteAddress, null, forwardedFor);
    }
    public String clientAddress(String remoteAddress, String forwarded, String forwardedFor) {
        String header = forwarded != null && !forwarded.isBlank() ? forwarded : forwardedFor;
        if (!isTrusted(remoteAddress) || header == null || header.isBlank()) return remoteAddress;
        String first = forwarded != null && !forwarded.isBlank()
                ? forwarded.split(",", 2)[0].trim().replaceFirst("(?i)^for=", "").split(";", 2)[0].trim().replaceAll("^\\\"|\\\"$", "")
                : forwardedFor.split(",", 2)[0].trim();
        try { InetAddress.getByName(first); return first; } catch (Exception ignored) { return remoteAddress; }
    }
    private boolean isTrusted(String address) { try { byte[] a=InetAddress.getByName(address).getAddress(); for(CidrRange r:trusted) if(r.matches(a)) return true; } catch(Exception ignored){} return false; }
    private static List<CidrRange> parse(List<String> values) { List<CidrRange> out=new ArrayList<>(); try { for(String value:values) out.add(CidrRange.parse(value)); } catch(Exception ex) { return List.of(); } return List.copyOf(out); }
    static final class CidrRange {
        private final byte[] network; private final int bits;
        private CidrRange(byte[] network,int bits){this.network=network;this.bits=bits;}
        static CidrRange parse(String value) throws Exception { String[] p=value.trim().split("/",2); byte[] n=InetAddress.getByName(p[0]).getAddress(); int b=p.length==2?Integer.parseInt(p[1]):n.length*8; if(b<0||b>n.length*8) throw new IllegalArgumentException(); return new CidrRange(n,b); }
        boolean matches(byte[] candidate){ if(candidate.length!=network.length)return false; int remaining=bits; for(int i=0;i<network.length&&remaining>0;i++){int take=Math.min(8,remaining);int mask=0xff<<(8-take);if((network[i]&mask)!=(candidate[i]&mask))return false;remaining-=take;}return true; }
    }
}
