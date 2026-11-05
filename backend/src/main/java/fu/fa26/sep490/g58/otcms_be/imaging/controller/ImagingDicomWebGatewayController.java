package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.security.ImagingDicomWebScopeResolver;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingViewerGrantService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Same-origin, grant-enforced streaming proxy to private Orthanc DICOMweb. */
@Controller
public class ImagingDicomWebGatewayController {
    private static final String PREFIX = "/dicom-web";
    private static final int STREAM_BUFFER_BYTES = 16 * 1024;
    private static final int MAX_JSON_RESPONSE_BYTES = 2 * 1024 * 1024;
    private static final List<String> REQUEST_HEADERS = List.of(HttpHeaders.ACCEPT, HttpHeaders.RANGE,
            HttpHeaders.IF_NONE_MATCH, HttpHeaders.IF_MODIFIED_SINCE);
    private static final List<String> RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE,
            HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_RANGE, HttpHeaders.ACCEPT_RANGES,
            HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED);

    private final ImagingProperties settings;
    private final ImagingDicomWebScopeResolver scopes;
    private final ImagingViewerGrantService grants;
    private final RestTemplate archiveClient;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final long reauthIntervalNanos;

    @Autowired
    public ImagingDicomWebGatewayController(ImagingProperties settings, ImagingDicomWebScopeResolver scopes,
            ImagingViewerGrantService grants) {
        this(settings, scopes, grants, Duration.ofSeconds(5));
    }

    ImagingDicomWebGatewayController(ImagingProperties settings, ImagingDicomWebScopeResolver scopes,
            ImagingViewerGrantService grants, Duration reauthInterval) {
        this.settings = settings;
        this.scopes = scopes;
        this.grants = grants;
        this.reauthIntervalNanos = reauthInterval.toNanos();
        Duration connectTimeout = positiveOrDefault(settings.getOrthancConnectTimeout(), Duration.ofSeconds(3));
        HttpClient client = HttpClient.newBuilder().connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER).build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(minimum(positiveOrDefault(settings.getOrthancResponseTimeout(), Duration.ofSeconds(5)),
                Duration.ofSeconds(5)));
        this.archiveClient = new RestTemplate(requests);
    }

    @RequestMapping("/dicom-web/**")
    public void proxy(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String method = request.getMethod();
        if (!HttpMethod.GET.matches(method) && !HttpMethod.HEAD.matches(method)) {
            writeDenial(response, HttpStatus.METHOD_NOT_ALLOWED);
            return;
        }
        URI archiveUri = settings.getOrthancBaseUri();
        if (archiveUri == null || archiveUri.getHost() == null || archiveUri.getUserInfo() != null
                || archiveUri.getQuery() != null || archiveUri.getFragment() != null
                || !("http".equalsIgnoreCase(archiveUri.getScheme())
                || "https".equalsIgnoreCase(archiveUri.getScheme()))) {
            writeDenial(response, HttpStatus.BAD_GATEWAY);
            return;
        }
        String relativePath = relativePath(request);
        if (relativePath == null) {
            writeDenial(response, HttpStatus.NOT_FOUND);
            return;
        }
        if (request.getQueryString() != null && request.getQueryString().length() > 4096) {
            writeDenial(response, HttpStatus.NOT_FOUND);
            return;
        }
        java.util.Optional<ImagingDicomWebScopeResolver.StudyScope> scope;
        try {
            scope = scopes.resolve(relativePath, request.getParameterMap());
        } catch (RuntimeException unavailable) {
            writeDenial(response, HttpStatus.BAD_GATEWAY);
            return;
        }
        if (scope.isEmpty()) {
            writeDenial(response, HttpStatus.NOT_FOUND);
            return;
        }
        String studyRef = scope.get().studyRef();
        String token = cookieValue(request.getCookies(), ImagingViewerGrantService.cookieName(studyRef));
        boolean authorized;
        try {
            authorized = grants.isAuthorized(studyRef, token);
        } catch (RuntimeException unavailable) {
            authorized = false;
        }
        if (!authorized) {
            writeDenial(response, HttpStatus.NOT_FOUND);
            return;
        }
        try {
            URI upstream = upstreamUri(archiveUri, relativePath, request.getQueryString());
            HttpMethod httpMethod = HttpMethod.valueOf(method);
            archiveClient.execute(upstream, httpMethod,
                    outgoing -> copyAllowedRequestHeaders(request, outgoing.getHeaders()),
                    upstreamResponse -> {
                        streamAuthorizedResponse(studyRef, token, upstreamResponse, response, method);
                        return null;
                    });
        } catch (RuntimeException exception) {
            if (!response.isCommitted()) writeDenial(response, HttpStatus.BAD_GATEWAY);
        }
    }

    private void streamAuthorizedResponse(String studyRef, String token, ClientHttpResponse upstream,
            HttpServletResponse response, String method) throws IOException {
        int status = upstream.getStatusCode().value();
        if (status == HttpStatus.NOT_FOUND.value()) {
            writeDenial(response, HttpStatus.NOT_FOUND);
            return;
        }
        if ((status < 200 || status >= 300) && status != HttpStatus.NOT_MODIFIED.value()) {
            writeDenial(response, HttpStatus.BAD_GATEWAY);
            return;
        }
        response.setStatus(status);
        boolean jsonResponse = isJson(upstream.getHeaders().getContentType());
        upstream.getHeaders().forEach((name, values) -> {
            if (!(jsonResponse && HttpHeaders.CONTENT_LENGTH.equalsIgnoreCase(name))
                    && RESPONSE_HEADERS.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(name))) {
                values.forEach(value -> response.addHeader(name, value));
            }
        });
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Accel-Buffering", "no");
        if ("HEAD".equals(method) || status == HttpStatus.NOT_MODIFIED.value()) return;

        if (jsonResponse) {
            writeSanitizedJson(studyRef, upstream.getBody(), response);
            return;
        }

        byte[] buffer = new byte[STREAM_BUFFER_BYTES];
        long lastAuthorizationCheck = System.nanoTime();
        try (InputStream input = upstream.getBody(); ServletOutputStream output = response.getOutputStream()) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                long now = System.nanoTime();
                if (now - lastAuthorizationCheck >= reauthIntervalNanos) {
                    if (!grants.isAuthorized(studyRef, token)) return;
                    lastAuthorizationCheck = now;
                }
                output.write(buffer, 0, read);
                output.flush();
            }
        }
    }

    private void writeSanitizedJson(String studyRef, InputStream input, HttpServletResponse response)
            throws IOException {
        byte[] body = readBounded(input, MAX_JSON_RESPONSE_BYTES);
        JsonNode document = jsonMapper.readTree(body);
        if (document == null || !sanitizeBulkDataUris(document, studyRef)) {
            writeDenial(response, HttpStatus.BAD_GATEWAY);
            return;
        }
        byte[] sanitized = jsonMapper.writeValueAsBytes(document);
        response.setContentLength(sanitized.length);
        response.getOutputStream().write(sanitized);
        response.getOutputStream().flush();
    }

    private boolean sanitizeBulkDataUris(JsonNode node, String studyRef) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            for (String property : node.propertyNames()) {
                JsonNode value = node.get(property);
                if ("BulkDataURI".equals(property)) {
                    if (value == null || !value.isString()) return false;
                    String rewritten = rewriteBulkDataUri(value.asString(), studyRef);
                    if (rewritten == null) return false;
                    object.put(property, rewritten);
                } else if (value != null && !sanitizeBulkDataUris(value, studyRef)) {
                    return false;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                if (!sanitizeBulkDataUris(child, studyRef)) return false;
            }
        }
        return true;
    }

    private String rewriteBulkDataUri(String value, String studyRef) {
        try {
            URI uri = URI.create(value);
            URI archive = settings.getOrthancBaseUri();
            if (uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getUserInfo() != null) return null;
            if (uri.isAbsolute() && (!uri.getScheme().equalsIgnoreCase(archive.getScheme())
                    || !uri.getHost().equalsIgnoreCase(archive.getHost()) || effectivePort(uri) != effectivePort(archive))) {
                return null;
            }
            String path = uri.getRawPath();
            if (path == null) return null;
            if (path.startsWith(PREFIX + "/")) path = path.substring(PREFIX.length());
            else if (!path.startsWith("/studies/")) return null;
            var referencedStudy = scopes.resolve(path, Map.of());
            if (referencedStudy.isEmpty() || !studyRef.equals(referencedStudy.get().studyRef())) return null;
            return PREFIX + path;
        } catch (RuntimeException invalidUri) {
            return null;
        }
    }

    private static byte[] readBounded(InputStream input, int maxBytes) throws IOException {
        try (InputStream source = input; ByteArrayOutputStream result = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[STREAM_BUFFER_BYTES];
            int count;
            while ((count = source.read(buffer)) != -1) {
                if (result.size() + count > maxBytes) throw new IOException("DICOM metadata response exceeds limit");
                result.write(buffer, 0, count);
            }
            return result.toByteArray();
        }
    }

    private static boolean isJson(MediaType contentType) {
        return contentType != null && "application".equalsIgnoreCase(contentType.getType())
                && contentType.getSubtype().toLowerCase(java.util.Locale.ROOT).contains("json");
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static URI upstreamUri(URI base, String relativePath, String query) {
        String baseText = base.toString().replaceAll("/$", "");
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseText)
                .path("/dicom-web").path(relativePath);
        if (query != null && !query.isBlank()) builder.query(query);
        return builder.build(true).toUri();
    }

    private static String relativePath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String context = request.getContextPath() == null ? "" : request.getContextPath();
        String expectedPrefix = context + PREFIX;
        if (requestUri == null || !requestUri.startsWith(expectedPrefix)) return null;
        String relative = requestUri.substring(expectedPrefix.length());
        return relative.isEmpty() ? "/" : relative;
    }

    private static void copyAllowedRequestHeaders(HttpServletRequest request, HttpHeaders target) {
        for (String name : REQUEST_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) target.set(name, value);
        }
        if (request.getHeader(HttpHeaders.ACCEPT) == null) {
            target.setAccept(List.of(MediaType.parseMediaType("application/dicom"), MediaType.APPLICATION_JSON,
                    MediaType.APPLICATION_OCTET_STREAM, MediaType.valueOf("multipart/related")));
        }
    }

    private static String cookieValue(Cookie[] cookies, String name) {
        if (cookies == null) return null;
        return Arrays.stream(cookies).filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue).findFirst().orElse(null);
    }

    private static Duration positiveOrDefault(Duration value, Duration fallback) {
        return value == null || value.isNegative() || value.isZero() ? fallback : value;
    }

    private static Duration minimum(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static void writeDenial(HttpServletResponse response, HttpStatus status) throws IOException {
        response.resetBuffer();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(("{\"status\":" + status.value()
                + ",\"error\":\"request_denied\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
