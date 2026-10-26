package fu.fa26.sep490.g58.otcms_be.auth.integration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import fu.fa26.sep490.g58.otcms_be.auth.exception.GoogleAuthenticationException;
import fu.fa26.sep490.g58.otcms_be.auth.service.GoogleOidcService;
import fu.fa26.sep490.g58.otcms_be.config.GoogleOidcProperties;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Map;

@Component
public class GoogleOidcClient implements GoogleOidcService {
    private final GoogleOidcProperties properties;
    private final RestClient client;
    public GoogleOidcClient(GoogleOidcProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.connectTimeoutMs()); factory.setReadTimeout(properties.readTimeoutMs());
        this.client = RestClient.builder().requestFactory(factory).baseUrl(properties.tokenUri()).build();
    }
    @Override
    @SuppressWarnings("unchecked")
    public VerifiedGoogleIdentity exchange(String code, String verifier, String redirectUri) {
        try {
            properties.requireConfigured();
            Map<String,Object> body = client.post().body(Map.of("code", code, "code_verifier", verifier,
                    "redirect_uri", redirectUri, "client_id", properties.audience(), "client_secret", properties.clientSecret(),
                    "grant_type", "authorization_code")).retrieve().body(Map.class);
            if (body == null || !(body.get("id_token") instanceof String idToken)) throw invalid();
            var claims = validate(idToken);
            return new VerifiedGoogleIdentity(claims.getStringClaim("sub"), claims.getStringClaim("email"),
                    Boolean.TRUE.equals(claims.getBooleanClaim("email_verified")));
        } catch (GoogleAuthenticationException ex) { throw ex; }
        catch (Exception ex) { throw invalid(); }
    }
    private com.nimbusds.jwt.JWTClaimsSet validate(String raw) throws Exception {
        SignedJWT jwt = SignedJWT.parse(raw);
        var processor = new DefaultJWTProcessor<com.nimbusds.jose.proc.SecurityContext>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256,
                new RemoteJWKSet<>(new URL(properties.jwkSetUri()))));
        var claims = processor.process(jwt, null);
        if (!properties.issuer().equals(claims.getIssuer()) || !claims.getAudience().contains(properties.audience())
                || claims.getExpirationTime() == null || claims.getExpirationTime().toInstant().isBefore(Instant.now())) throw invalid();
        return claims;
    }
    private static GoogleAuthenticationException invalid() { return new GoogleAuthenticationException("AUTHENTICATION_FAILED"); }
}
