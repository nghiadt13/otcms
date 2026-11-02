package fu.fa26.sep490.g58.otcms_be.auth.security;


import fu.fa26.sep490.g58.otcms_be.auth.model.entity.enums.AccountType;
import fu.fa26.sep490.g58.otcms_be.config.SessionSecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class JwtTokenService {
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private final Clock clock;
    private final SessionSecurityProperties properties;

    @Autowired public JwtTokenService(SessionSecurityProperties properties) { this(properties, Clock.systemUTC()); }
    public JwtTokenService(SessionSecurityProperties properties, Clock clock) { this.properties = properties; this.clock = clock; }
    public JwtTokenService(Clock clock, String configuredSecret) {
        this(new SessionSecurityProperties(configuredSecret, 900, 604800, "Strict", true, java.util.List.of(), java.util.List.of()), clock);
    }

    public String issue(UUID accountId, AccountType type, UUID sessionId) {
        long now = Instant.now(clock).getEpochSecond();
        String payload = enc("{\"sub\":\"" + accountId + "\",\"accountType\":\"" + type + "\",\"sid\":\"" + sessionId + "\",\"iat\":" + now + ",\"exp\":" + (now + properties.accessTokenSeconds()) + "}");
        String input = enc(HEADER) + "." + payload;
        return input + "." + enc(sign(input));
    }

    public Claims validate(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 3 || !HEADER.equals(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8))) throw invalid();
            byte[] providedSignature = Base64.getUrlDecoder().decode(parts[2]);
            if (!parts[2].equals(enc(providedSignature))
                    || !MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), providedSignature)) throw invalid();
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            Claims claims = new Claims(UUID.fromString(text(json, "sub")), AccountType.valueOf(text(json, "accountType")), UUID.fromString(text(json, "sid")), number(json, "iat"), number(json, "exp"));
            long now = Instant.now(clock).getEpochSecond();
            if (claims.exp() <= now || claims.iat() > now + 60 || claims.exp() - claims.iat() != properties.accessTokenSeconds()) throw invalid();
            return claims;
        } catch (JwtValidationException ex) { throw ex; }
        catch (RuntimeException ex) { throw invalid(); }
    }

    private byte[] sign(String value) { try { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(properties.requiredJwtSecret(), "HmacSHA256")); return mac.doFinal(value.getBytes(StandardCharsets.UTF_8)); } catch (IllegalStateException e) { throw e; } catch (Exception e) { throw new IllegalStateException("JWT signing unavailable", e); } }
    private static String text(String json,String name){return group(json,"\\\""+name+"\\\":\\\"([^\\\"]+)\\\"");}
    private static long number(String json,String name){return Long.parseLong(group(json,"\\\""+name+"\\\":([0-9]+)"));}
    private static String group(String json,String regex){Matcher m=Pattern.compile(regex).matcher(json);if(!m.find())throw invalid();return m.group(1);}
    private static JwtValidationException invalid(){return new JwtValidationException();}
    private static String enc(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String enc(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
        public static class Claims {
    private final UUID accountId;
    private final AccountType accountType;
    private final UUID sessionId;
    private final long iat;
    private final long exp;
    public Claims(UUID accountId, AccountType accountType, UUID sessionId, long iat, long exp) {
        this.accountId = accountId;
        this.accountType = accountType;
        this.sessionId = sessionId;
        this.iat = iat;
        this.exp = exp;
    }
    public UUID getAccountId() { return accountId; }
    public UUID accountId() { return accountId; }
    public AccountType getAccountType() { return accountType; }
    public AccountType accountType() { return accountType; }
    public UUID getSessionId() { return sessionId; }
    public UUID sessionId() { return sessionId; }
    public long getIat() { return iat; }
    public long iat() { return iat; }
    public long getExp() { return exp; }
    public long exp() { return exp; }
}
    public static final class JwtValidationException extends RuntimeException { public JwtValidationException(){super("Invalid access token");} }
}
