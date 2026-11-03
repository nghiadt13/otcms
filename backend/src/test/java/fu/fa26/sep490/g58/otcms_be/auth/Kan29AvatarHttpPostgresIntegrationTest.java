package fu.fa26.sep490.g58.otcms_be.auth;

import fu.fa26.sep490.g58.otcms_be.auth.service.AuthSessionService;
import fu.fa26.sep490.g58.otcms_be.support.LocalPostgresTest;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

/** Separate context: MockMvc csrf() deliberately replaces the CSRF repository on its filter. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@LocalPostgresTest
class Kan29AvatarHttpPostgresIntegrationTest {
    static final String SCHEMA="kan29_http_"+UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        String ephemeral=UUID.randomUUID().toString()+UUID.randomUUID();
        r.add("spring.flyway.schemas",()->SCHEMA);
        r.add("spring.flyway.default-schema",()->SCHEMA);
        r.add("spring.datasource.hikari.schema",()->SCHEMA);
        r.add("app.auth.session-security.jwt-secret",()->ephemeral);
        r.add("app.auth.rate-limit-hmac-secret",()->ephemeral);
    }
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthSessionService sessions;
    final JsonMapper json=JsonMapper.builder().findAndAddModules().build();

    @Test void realServletMultipartHonorsExactTwoMiBAndReturnsSafeOversizeError() throws Exception {
        UUID account=UUID.randomUUID();
        jdbc.update("insert into accounts(id,normalized_email,account_type,status,created_at,updated_at) values (?,?,'PATIENT','ACTIVE',now(),now())",account,account+"@example.test");
        String token=sessions.create(account,"127.0.0.1","kan29-http").body().accessToken();
        var client=HttpClient.newHttpClient();
        var csrfResponse=client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertEquals(200,csrfResponse.statusCode());
        String csrfToken=json.readTree(csrfResponse.body()).path("csrfToken").asString();
        assertFalse(csrfToken.isBlank());
        String boundary="kan29-boundary";
        for(int size:new int[]{2097152,2097153}) {
            byte[] image=new byte[size]; byte[] sig={(byte)137,80,78,71,13,10,26,10}; System.arraycopy(sig,0,image,0,8);
            var body=new java.io.ByteArrayOutputStream();
            body.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"avatar.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            body.write(image); body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
            String correlation=UUID.randomUUID().toString();
            var request=HttpRequest.newBuilder(uri("/api/v1/me/avatar"))
                    .header("Authorization","Bearer "+token).header("Content-Type","multipart/form-data; boundary="+boundary)
                    .header("Cookie","XSRF-TOKEN="+csrfToken).header("X-CSRF-TOKEN",csrfToken).header("X-Correlation-ID",correlation)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
            var response=client.send(request,HttpResponse.BodyHandlers.ofString());
            assertEquals(size==2097152?200:400,response.statusCode(),"Upload content size: "+size);
            if(size>2097152) {
                assertEquals("MSG-08",json.readTree(response.body()).path("error").path("code").asString());
                assertEquals(correlation,json.readTree(response.body()).path("error").path("correlationId").asString());
            }
        }
    }
    URI uri(String path) { return URI.create("http://127.0.0.1:"+port+path); }
}
