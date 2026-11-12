package fu.fa26.sep490.g58.otcms_be.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Writes the live OpenAPI document to {@code plans/261113-final-db/.snapshots/openapi-current.json} so the final-db refactor can
 * prove the API contract did not change: compare it with {@code plans/261113-final-db/api-baseline.json}
 * using {@code plans/261113-final-db/tools/compare-openapi.mjs}.
 */
@SpringBootTest
@LocalPostgresTest
@AutoConfigureMockMvc
class OpenApiContractSnapshotTest {

    // Under plans/ (git-ignored for new files), relative to the backend module directory.
    static final Path SNAPSHOT = Path.of("..", "plans", "261113-final-db", ".snapshots", "openapi-current.json");

    @Autowired MockMvc mvc;

    @Test
    void writesOpenApiSnapshot() throws Exception {
        var body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Files.createDirectories(SNAPSHOT.getParent());
        Files.writeString(SNAPSHOT, body, StandardCharsets.UTF_8);
    }
}
