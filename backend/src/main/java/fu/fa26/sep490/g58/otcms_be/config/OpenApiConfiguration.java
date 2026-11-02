package fu.fa26.sep490.g58.otcms_be.config;

import fu.fa26.sep490.g58.otcms_be.common.api.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
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
import java.util.LinkedHashMap;

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
            applyReviewAndNotificationContractResponses(openApi);
            applyPatientProfileContractResponses(openApi);
        };
    }

    private void applyPatientProfileContractResponses(OpenAPI openApi) {
        String root = "/api/v1/patient-profiles";
        Map<String, Map<PathItem.HttpMethod, List<String>>> contracts = Map.of(
                root, Map.of(PathItem.HttpMethod.POST, List.of("400", "401", "403", "409", "500")),
                root + "/matches", Map.of(PathItem.HttpMethod.GET, List.of("400", "401", "403", "409", "500")),
                root + "/{id}", Map.of(
                        PathItem.HttpMethod.GET, List.of("400", "401", "403", "404", "500"),
                        PathItem.HttpMethod.PUT, List.of("400", "401", "403", "404", "409", "500")));
        contracts.forEach((path, methods) -> {
            PathItem item = openApi.getPaths().get(path);
            if (item == null) return;
            methods.forEach((method, statuses) -> {
                Operation operation = item.readOperationsMap().get(method);
                if (operation == null) return;
                statuses.forEach(status -> {
                    ApiResponse response = operation.getResponses().get(status);
                    if (response == null) response = new ApiResponse().description("Patient profile request rejected");
                    response.setContent(new Content().addMediaType("application/json", new MediaType()
                            .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
                    response.addHeaderObject("X-Correlation-ID", new io.swagger.v3.oas.models.headers.Header()
                            .description("Request correlation identifier").schema(new Schema<>().type("string")));
                    operation.getResponses().addApiResponse(status, response);
                });
            });
        });
        if (openApi.getPaths().get(root) != null
                && openApi.getPaths().get(root).getPost() != null)
            setCreatedResponse(openApi, root, "PatientProfileResponse", false);
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

    private record FeatureContract(String path, PathItem.HttpMethod method, String success,
            Map<String, String> errors) { }

    private void applyReviewAndNotificationContractResponses(OpenAPI openApi) {
        Map<String, String> patient = Map.of("401", "", "403", "MSG-56", "500", "MSG-04");
        Map<String, String> admin = Map.of("401", "", "403", "", "500", "MSG-04");
        Map<String, String> inbox = Map.of("401", "", "500", "MSG-04");
        var contracts = List.of(
                new FeatureContract("/api/v1/me/reviews/eligible-visits", PathItem.HttpMethod.GET, "200", patient),
                new FeatureContract("/api/v1/me/reviews", PathItem.HttpMethod.GET, "200", patient),
                new FeatureContract("/api/v1/reviews", PathItem.HttpMethod.POST, "201", featureErrors(patient, "400", "MSG-57", "409", "MSG-56")),
                new FeatureContract("/api/v1/public/reviews", PathItem.HttpMethod.GET, "200", Map.of("400", "MSG-54", "500", "MSG-04")),
                new FeatureContract("/api/v1/admin/reviews", PathItem.HttpMethod.GET, "200", featureErrors(admin, "400", "MSG-54")),
                new FeatureContract("/api/v1/admin/reviews/{id}", PathItem.HttpMethod.GET, "200", featureErrors(admin, "404", "MSG-50")),
                new FeatureContract("/api/v1/admin/reviews/{id}/approve", PathItem.HttpMethod.POST, "200", featureErrors(admin, "404", "MSG-50", "409", "MSG-56")),
                new FeatureContract("/api/v1/admin/reviews/{id}/hide", PathItem.HttpMethod.POST, "200", featureErrors(admin, "404", "MSG-50", "409", "MSG-56")),
                new FeatureContract("/api/v1/admin/reviews/{id}/unhide", PathItem.HttpMethod.POST, "200", featureErrors(admin, "404", "MSG-50", "409", "MSG-56")),
                new FeatureContract("/api/v1/admin/reviews/{id}/reply", PathItem.HttpMethod.PUT, "200", featureErrors(admin, "400", "MSG-57", "404", "MSG-50", "409", "MSG-56")),
                new FeatureContract("/api/v1/me/notifications", PathItem.HttpMethod.GET, "200", featureErrors(inbox, "400", "MSG-54")),
                new FeatureContract("/api/v1/me/notifications/unread-count", PathItem.HttpMethod.GET, "200", inbox),
                new FeatureContract("/api/v1/me/notifications/{id}/read", PathItem.HttpMethod.POST, "200", featureErrors(inbox, "404", "MSG-50")),
                new FeatureContract("/api/v1/me/notifications/read-all", PathItem.HttpMethod.POST, "200", inbox));
        addReviewAndNotificationErrorExamples(openApi.getComponents());
        for (var contract : contracts) {
            var path = openApi.getPaths().get(contract.path());
            if (path == null) continue;
            var operation = path.readOperationsMap().get(contract.method());
            if (operation == null) continue;
            // Keep inferred DTO schemas and controller descriptions; customize only these fourteen operations.
            var success = operation.getResponses().get(contract.success());
            if (success != null) normalizeJsonContentType(success);
            contract.errors().forEach((status, message) -> operation.getResponses().addApiResponse(status,
                    featureErrorResponse(status, message)));
            operation.getResponses().values().forEach(this::normalizeJsonContentType);
            operation.setSecurity(contract.path().equals("/api/v1/public/reviews") ? List.of()
                    : List.of(new SecurityRequirement().addList("bearerAuth")));
        }
        addReviewWriteExamples(openApi);
    }

    private Map<String, String> featureErrors(Map<String, String> base, String... statusAndMessage) {
        var errors = new LinkedHashMap<>(base);
        for (int i = 0; i < statusAndMessage.length; i += 2) errors.put(statusAndMessage[i], statusAndMessage[i + 1]);
        return errors;
    }

    private ApiResponse featureErrorResponse(String status, String message) {
        String description = message.isEmpty() ? (status.equals("401") ? "Authentication required" : "Role access denied")
                : message + ": " + switch (message) {
                    case "MSG-57" -> "Invalid fields; fieldErrors is at the response root";
                    case "MSG-54" -> "Invalid query, filter, date range or sort";
                    case "MSG-50" -> "Record missing or unavailable to the current account";
                    case "MSG-56" -> status.equals("403") ? "Visit/profile outside own scope"
                            : "Invalid transition, duplicate review or optimistic conflict";
                    default -> "Request processing failed";
                };
        var media = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
        if (!message.isEmpty()) media.addExamples(message, new Example().$ref("#/components/examples/" + message));
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json", media));
    }

    private void addReviewAndNotificationErrorExamples(Components components) {
        for (String code : List.of("MSG-57", "MSG-54", "MSG-50", "MSG-56", "MSG-04")) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("error", Map.of("code", code, "message", code.equals("MSG-57")
                    ? "Review request could not be completed" : "Request could not be completed"));
            if (code.equals("MSG-57")) value.put("fieldErrors", List.of(Map.of("field", "text", "code", code)));
            components.addExamples(code, new Example().summary(code + " shared error envelope").value(value));
        }
    }

    private void addReviewWriteExamples(OpenAPI openApi) {
        var submitPath = openApi.getPaths().get("/api/v1/reviews");
        if (submitPath != null && submitPath.getPost() != null) {
            addWriteExamples(submitPath.getPost(), "201", """
                    {"visitId":"7b6d9d88-bf52-4d9d-8cb2-86cf44e16a18","rating":5,"text":"Dịch vụ tốt, bác sĩ tư vấn rõ ràng."}
                    """, """
                    {
                      "id":"9c495ace-cc80-4c9c-b6f6-19bf0ac85346",
                      "visitId":"7b6d9d88-bf52-4d9d-8cb2-86cf44e16a18",
                      "visitDate":"2026-11-01","rating":5,"text":"Dịch vụ tốt, bác sĩ tư vấn rõ ràng.",
                      "status":"PENDING","clinicReply":null,"createdAt":"2026-11-01T19:00:00+07:00",
                      "patient":{"fullName":"Nguyễn Văn A","relationship":"SELF"}
                    }
                    """);
        }
        var replyPath = openApi.getPaths().get("/api/v1/admin/reviews/{id}/reply");
        if (replyPath != null && replyPath.getPut() != null) {
            addWriteExamples(replyPath.getPut(), "200", """
                    {"text":"Cảm ơn bạn đã gửi đánh giá cho phòng khám."}
                    """, """
                    {
                      "id":"9c495ace-cc80-4c9c-b6f6-19bf0ac85346",
                      "submittedAt":"2026-11-01T19:00:00+07:00","patientName":"Nguyễn Văn A","rating":5,
                      "content":"Dịch vụ tốt, bác sĩ tư vấn rõ ràng.","status":"PUBLISHED","replied":true,
                      "visit":{"visitDate":"2026-11-01","doctorName":"Trần Văn B","serviceName":"Khám chuyên khoa"},
                      "allowedActions":["hide","editReply"],
                      "reply":{"text":"Cảm ơn bạn đã gửi đánh giá cho phòng khám.","repliedAt":"2026-11-02T09:30:00+07:00"}
                    }
                    """);
        }
    }

    private void addWriteExamples(Operation operation, String success, String request, String response) {
        operation.getRequestBody().getContent().get("application/json").setExample(openApiExample(request));
        operation.getResponses().get(success).getContent().get("application/json").setExample(openApiExample(response));
    }

    private Object openApiExample(String json) {
        try {
            // Swagger's own JSON tree preserves explicit nulls in examples such as MyReview.clinicReply.
            return io.swagger.v3.core.util.Json.mapper().readTree(json);
        } catch (java.io.IOException invalidExample) {
            throw new IllegalArgumentException("Invalid static OpenAPI example", invalidExample);
        }
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
                || "/api/v1/public/reviews".equals(path)
                || "/api/v1/appointments/availability".equals(path)
                || "/api/v1/doctors".equals(path)
                || path.matches("/api/v1/doctors/\\{[^/]+\\}");
    }
}
