package fu.fa26.sep490.g58.otcms_be.config;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfiguration {
    @Bean
    OpenAPI otcmsOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("OTCMS REST API")
                        .version("1.0.0")
                        .description("Spring Boot API. Business authorization remains enforced by the backend."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }

    @Bean
    OpenApiCustomizer bearerSecurityForProtectedOperations() {
        return openApi -> {
            ModelConverters.getInstance().read(ErrorResponse.class)
                    .forEach((name, schema) -> openApi.getComponents().addSchemas(name, schema));
            if (openApi.getPaths() == null) return;
            addSharedResponses(openApi.getComponents());
            markStrictRequestSchemas(openApi.getComponents());
            openApi.getPaths().forEach((path, pathItem) -> pathItem.readOperationsMap().forEach((method, operation) -> {
                if (path.startsWith("/api/v1/") && !isPublicGet(path, method.name())) {
                    operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
                }
            }));
            applyAppointmentContractResponses(openApi);
        };
    }

    private void markStrictRequestSchemas(Components components) {
        List.of("CreateAppointmentRequest", "RescheduleAppointmentRequest", "CancelAppointmentRequest",
                "ReassignAppointmentRequest", "UpdateAppointmentStatusRequest", "CheckInRequest",
                "FollowUpRequest", "WalkInRequest").forEach(name -> {
                    Schema schema = components.getSchemas().get(name);
                    if (schema != null) schema.setAdditionalProperties(Boolean.FALSE);
                });
    }

    private void addSharedResponses(Components components) {
        ApiResponse error = new ApiResponse().description("Safe API error envelope")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiError"))))
                .addHeaderObject("X-Correlation-ID", new io.swagger.v3.oas.models.headers.Header()
                        .description("Request correlation identifier").schema(new Schema<>().type("string")));
        components.addResponses("ApiError", error);
        components.addResponses("ClientError", new ApiResponse().description("Invalid request")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ApiError"))))
                .addHeaderObject("X-Correlation-ID", new io.swagger.v3.oas.models.headers.Header()
                        .description("Request correlation identifier").schema(new Schema<>().type("string"))));
    }

    private void applyAppointmentContractResponses(OpenAPI openApi) {
        Map<String, Map<PathItem.HttpMethod, List<String>>> responses = Map.ofEntries(
                Map.entry("/api/v1/appointments/availability", Map.of(PathItem.HttpMethod.GET, List.of("400", "429", "503"))),
                Map.entry("/api/v1/staff/appointments/availability", Map.of(PathItem.HttpMethod.GET, List.of("400", "401", "403", "503"))),
                Map.entry("/api/v1/appointments/{appointmentId}/reschedule-availability", Map.of(PathItem.HttpMethod.GET, List.of("400", "401", "403", "404"))),
                Map.entry("/api/v1/appointments/{appointmentId}/reassignment-availability", Map.of(PathItem.HttpMethod.GET, List.of("400", "401", "403", "404"))),
                Map.entry("/api/v1/appointments", Map.of(
                        PathItem.HttpMethod.GET, List.of("400", "401", "403"),
                        PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/appointments/{id}", Map.of(PathItem.HttpMethod.GET, List.of("401", "403", "404"))),
                Map.entry("/api/v1/appointments/{id}/reschedule", Map.of(PathItem.HttpMethod.PATCH, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/appointments/{id}/cancel", Map.of(PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/appointments/{id}/reassign", Map.of(PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/appointments/{id}/check-in", Map.of(PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/appointments/{id}/status", Map.of(PathItem.HttpMethod.PATCH, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/visits/{visitId}/follow-up-appointments", Map.of(PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/visits/walk-ins", Map.of(PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "503"))),
                Map.entry("/api/v1/system/production-readiness", Map.of(PathItem.HttpMethod.GET, List.of("401"))));

        responses.forEach((path, byMethod) -> {
            PathItem pathItem = openApi.getPaths().get(path);
            if (pathItem == null) return;
            byMethod.forEach((method, codes) -> {
                Operation operation = pathItem.readOperationsMap().get(method);
                if (operation == null) return;
                for (String code : codes) {
                    operation.getResponses().addApiResponse(code,
                            new ApiResponse().$ref("#/components/responses/" + ("400".equals(code) ? "ClientError" : "ApiError")));
                }
            });
        });

        setCreatedResponse(openApi, "/api/v1/appointments", "Appointment", true);
        setCreatedResponse(openApi, "/api/v1/visits/{visitId}/follow-up-appointments", "Appointment", true);
        setCreatedResponse(openApi, "/api/v1/visits/walk-ins", "WalkInResponse", false);
        responses.forEach((path, byMethod) -> {
            PathItem pathItem = openApi.getPaths().get(path);
            if (pathItem == null) return;
            byMethod.keySet().forEach(method -> {
                Operation operation = pathItem.readOperationsMap().get(method);
                if (operation == null || operation.getResponses() == null) return;
                operation.getResponses().values().forEach(this::normalizeJsonContentType);
            });
        });
    }

    private void normalizeJsonContentType(ApiResponse response) {
        if (response.getContent() == null || !response.getContent().containsKey("*/*")) return;
        MediaType mediaType = response.getContent().remove("*/*");
        response.getContent().addMediaType("application/json", mediaType);
    }

    private void setCreatedResponse(OpenAPI openApi, String path, String schemaName, boolean includeLocation) {
        Operation operation = openApi.getPaths().get(path).readOperationsMap().get(PathItem.HttpMethod.POST);
        ApiResponse response = new ApiResponse().description("Resource created")
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/" + schemaName))))
                .addHeaderObject("X-Correlation-ID", new io.swagger.v3.oas.models.headers.Header()
                        .description("Request correlation identifier").schema(new Schema<>().type("string")));
        if (includeLocation) {
            response.addHeaderObject("Location", new io.swagger.v3.oas.models.headers.Header()
                    .description("URI of the created appointment").schema(new Schema<>().type("string").format("uri-reference")));
        }
        operation.getResponses().remove("200");
        operation.getResponses().addApiResponse("201", response);
    }

    private boolean isPublicGet(String path, String method) {
        if (!"GET".equals(method)) return false;
        return "/api/v1/clinic-services".equals(path)
                || "/api/v1/appointments/availability".equals(path)
                || "/api/v1/doctors".equals(path)
                || path.matches("/api/v1/doctors/\\{[^/]+\\}");
    }
}
