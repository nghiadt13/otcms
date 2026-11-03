package fu.fa26.sep490.g58.otcms_be.content;
import fu.fa26.sep490.g58.otcms_be.content.repository.*;
import fu.fa26.sep490.g58.otcms_be.content.exception.*;
import fu.fa26.sep490.g58.otcms_be.content.service.impl.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
/** Optional recording harness for comparing the same HTTP tests before/after KAN-44. */
@TestConfiguration(proxyBeanMethods = false)
public class HttpContractCapture {
    @Bean
    MockMvcBuilderCustomizer contractCapture(Environment environment, tools.jackson.databind.ObjectMapper json) {
        return builder -> builder.alwaysDo(result -> {
            String directory = environment.getProperty("kan44.contract.dir");
            if (directory == null) return;
            String owner = StackWalker.getInstance().walk(frames -> frames
            .filter(frame -> frame.getClassName().endsWith("Test")
            && frame.getClassName().contains("otcms_be"))
            .map(frame -> frame.getClassName() + "." + frame.getMethodName())
            .findFirst().orElse("request"));
            var request = result.getRequest();
            var response = result.getResponse();
            String requestBody = request.getContentAsString();
            if (requestBody != null && requestBody.stripLeading().startsWith("{")) {
                requestBody = json.writeValueAsString(json.readTree(requestBody));
            }
            String key = owner + " " + request.getMethod() + " " + request.getRequestURI()
            + "?" + request.getQueryString() + " " + request.getParameterMap().keySet()
            + " " + requestBody;
            String normalizedKey = normalize(key);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(normalizedKey.getBytes(StandardCharsets.UTF_8)));
            String body = response.getContentType() != null && response.getContentType().contains("json")
            ? normalize(response.getContentAsString(StandardCharsets.UTF_8))
            : HexFormat.of().formatHex(response.getContentAsByteArray());
            var folder = Path.of(directory);
            Files.createDirectories(folder);
            Files.writeString(folder.resolve(digest + ".txt"), normalizedKey + "\n"
            + response.getStatus() + "\n" + body, StandardCharsets.UTF_8);
        });
    }
    private static String normalize(String value) {
        return value.replaceAll("K40_[0-9a-fA-F]{8}", "K40_<suffix>").replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}", "<uuid>")
        .replaceAll("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+(?:Z|[+-][0-9]{2}:[0-9]{2})", "<timestamp>");
    }
}
