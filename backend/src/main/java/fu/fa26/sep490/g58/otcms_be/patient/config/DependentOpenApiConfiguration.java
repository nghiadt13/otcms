package fu.fa26.sep490.g58.otcms_be.patient.config;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.*;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;

/** R3 metadata only. Existing API contracts and global OpenAPI configuration stay unchanged. */
@Configuration
public class DependentOpenApiConfiguration {
    @Bean
    OpenApiCustomizer dependentR3Contract() {
        return api -> {
            ModelConverters.getInstance().read(ErrorResponse.class)
                    .forEach((name, schema) -> api.getComponents().addSchemas(name, schema));
            if (api.getPaths() == null) return;
            api.getPaths().forEach((path, item) -> {
                if (!path.startsWith("/api/v1/me/dependents") && !path.startsWith("/api/v1/care-links")
                        && !path.equals("/api/v1/me/patient-profiles")) return;
                item.readOperations().forEach(operation -> {
                    operation.setDescription((path.startsWith("/api/v1/care-links") ? "Active RECEPTIONIST only. " : "Active PATIENT only. ")
                            + "VALIDATION_FAILED: MSG-17; CARE_LINK_NOT_AVAILABLE: MSG-21; OTP_INVALID: wrong, expired or unavailable code (MSG-82). "
                            + "Administrative fields only; ownership is checked before link-state disclosure. Mutations require CSRF.");
                    Map.of("400", "VALIDATION_FAILED / OTP_INVALID", "401", "Authentication required", "403", "ACCESS_DENIED",
                            "404", "CARE_LINK_NOT_AVAILABLE", "409", "CARE_LINK_NOT_AVAILABLE / STALE_VERSION",
                            "429", "RATE_LIMIT_EXCEEDED", "503", "EMAIL_DELIVERY_UNAVAILABLE", "500", "MSG-04")
                            .forEach((status, description) -> operation.getResponses().addApiResponse(status,
                                    new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))))
                                            .addHeaderObject("X-Correlation-ID", new Header().schema(new Schema<>().type("string")))));
                });
                if (path.equals("/api/v1/me/dependents") && item.getPost() != null) {
                    item.getPost().getResponses().remove("200");
                    var content = new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/LinkResult")));
                    item.getPost().getResponses().addApiResponse("201", new ApiResponse().description("LINKED: new accountless profile").content(content));
                    item.getPost().getResponses().addApiResponse("202", new ApiResponse().description("OTP_SENT with maskedEmail or COUNTER_REQUIRED").content(content));
                }
                if (path.endsWith("/otp/resend") && item.getPost() != null) {
                    item.getPost().getResponses().remove("200");
                    item.getPost().getResponses().addApiResponse("202", new ApiResponse().description("OTP_SENT with linkId and maskedEmail")
                            .content(new Content().addMediaType("application/json", new MediaType()
                                    .schema(new Schema<>().$ref("#/components/schemas/LinkResult")))));
                }
                if (item.getDelete() != null) {
                    item.getDelete().getResponses().remove("200");
                    item.getDelete().getResponses().addApiResponse("204", new ApiResponse().description("Link revoked; clinic history retained"));
                }
                if (path.endsWith("/counter-rejection") && item.getPost() != null) {
                    item.getPost().getResponses().remove("200");
                    item.getPost().getResponses().addApiResponse("204", new ApiResponse().description("Counter request revoked"));
                }
            });
        };
    }
}
