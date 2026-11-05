package fu.fa26.sep490.g58.otcms_be.imaging.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import fu.fa26.sep490.g58.otcms_be.imaging.config.ImagingProperties;
import fu.fa26.sep490.g58.otcms_be.imaging.security.ImagingDicomWebScopeResolver;
import fu.fa26.sep490.g58.otcms_be.imaging.service.impl.ImagingViewerGrantService;
import java.net.URI;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.sun.net.httpserver.HttpServer;

@ExtendWith(MockitoExtension.class)
class ImagingDicomWebGatewayControllerTest {
    @Mock ImagingDicomWebScopeResolver scopes;
    @Mock ImagingViewerGrantService grants;

    @Test
    void deniesUnscopedStudyEnumerationWithGenericNoStoreResponse() throws Exception {
        ImagingProperties settings = settings();
        var controller = new ImagingDicomWebGatewayController(settings, scopes, grants);
        var request = new MockHttpServletRequest("GET", "/dicom-web/studies");
        var response = new MockHttpServletResponse();
        when(scopes.resolve("/studies", Map.of())).thenReturn(Optional.empty());

        controller.proxy(request, response);

        assertEquals(404, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getContentAsString().contains("request_denied"));
        verifyNoInteractions(grants);
    }

    @Test
    void rejectsWriteMethodsBeforeResolvingStudyOrGrant() throws Exception {
        var controller = new ImagingDicomWebGatewayController(settings(), scopes, grants);
        var request = new MockHttpServletRequest("POST", "/dicom-web/studies/1.2.3");
        var response = new MockHttpServletResponse();

        controller.proxy(request, response);

        assertEquals(405, response.getStatus());
        assertTrue(response.getContentAsString().contains("request_denied"));
        verifyNoInteractions(scopes, grants);
    }

    @Test
    void streamsAuthorizedStudyWithoutForwardingBrowserCredentials() throws Exception {
        byte[] dicomBytes = "dicom-response".getBytes(StandardCharsets.UTF_8);
        AtomicReference<String> cookieReceived = new AtomicReference<>();
        AtomicReference<String> authorizationReceived = new AtomicReference<>();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/dicom-web/studies/1.2.3", exchange -> {
            cookieReceived.set(exchange.getRequestHeaders().getFirst("Cookie"));
            authorizationReceived.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.getResponseHeaders().set("Content-Type", "application/dicom");
            exchange.getResponseHeaders().set("Set-Cookie", "provider=secret");
            exchange.sendResponseHeaders(200, dicomBytes.length);
            exchange.getResponseBody().write(dicomBytes);
            exchange.close();
        });
        upstream.start();
        try {
            String studyRef = "opaque-study";
            when(scopes.resolve(eq("/studies/1.2.3"), anyMap()))
                    .thenReturn(Optional.of(new ImagingDicomWebScopeResolver.StudyScope(studyRef, "1.2.3")));
            when(grants.isAuthorized(eq(studyRef), eq("opaque-token"))).thenReturn(true);
            ImagingProperties properties = new ImagingProperties();
            properties.setOrthancBaseUri(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
            var controller = new ImagingDicomWebGatewayController(properties, scopes, grants);
            var request = new MockHttpServletRequest("GET", "/dicom-web/studies/1.2.3");
            request.setCookies(new jakarta.servlet.http.Cookie(
                    ImagingViewerGrantService.cookieName(studyRef), "opaque-token"));
            request.addHeader("Authorization", "Bearer browser-secret");
            request.addHeader("Cookie", "session=browser-secret");
            var response = new MockHttpServletResponse();

            controller.proxy(request, response);

            assertEquals(200, response.getStatus());
            assertArrayEquals(dicomBytes, response.getContentAsByteArray());
            assertEquals("private, no-store", response.getHeader("Cache-Control"));
            assertEquals(null, response.getHeader("Set-Cookie"));
            assertEquals(null, cookieReceived.get());
            assertEquals(null, authorizationReceived.get());
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void rewritesMetadataBulkDataReferencesToTheAuthorizedGatewayScope() throws Exception {
        String studyRef = "opaque-study";
        String internalBulkUri = "http://127.0.0.1:%d/dicom-web/studies/1.2.3/series/1.2.4/instances/1.2.5/bulkdata/7fe00010";
        AtomicReference<String> sourceJson = new AtomicReference<>();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        sourceJson.set("[{\"7fe00010\":{\"vr\":\"OB\",\"BulkDataURI\":\""
                + internalBulkUri.formatted(upstream.getAddress().getPort()) + "\"}}]");
        byte[] source = sourceJson.get().getBytes(StandardCharsets.UTF_8);
        upstream.createContext("/dicom-web/studies/1.2.3/metadata", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/dicom+json");
            exchange.sendResponseHeaders(200, source.length);
            exchange.getResponseBody().write(source);
            exchange.close();
        });
        upstream.start();
        try {
            when(scopes.resolve(eq("/studies/1.2.3/metadata"), anyMap()))
                    .thenReturn(Optional.of(new ImagingDicomWebScopeResolver.StudyScope(studyRef, "1.2.3")));
            when(scopes.resolve(eq("/studies/1.2.3/series/1.2.4/instances/1.2.5/bulkdata/7fe00010"), anyMap()))
                    .thenReturn(Optional.of(new ImagingDicomWebScopeResolver.StudyScope(studyRef, "1.2.3")));
            when(grants.isAuthorized(studyRef, "opaque-token")).thenReturn(true);
            ImagingProperties properties = new ImagingProperties();
            properties.setOrthancBaseUri(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
            var controller = new ImagingDicomWebGatewayController(properties, scopes, grants);
            var request = new MockHttpServletRequest("GET", "/dicom-web/studies/1.2.3/metadata");
            request.setCookies(new jakarta.servlet.http.Cookie(
                    ImagingViewerGrantService.cookieName(studyRef), "opaque-token"));
            var response = new MockHttpServletResponse();

            controller.proxy(request, response);

            assertEquals(200, response.getStatus());
            String sanitized = response.getContentAsString();
            assertTrue(sanitized.contains("/dicom-web/studies/1.2.3/series/1.2.4/instances/1.2.5/bulkdata/7fe00010"));
            assertTrue(!sanitized.contains("http://127.0.0.1"));
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void terminatesAnActiveStreamWhenPeriodicOwnerRecheckDenies() throws Exception {
        AtomicInteger checks = new AtomicInteger();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/dicom-web/studies/1.2.3", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/octet-stream");
            exchange.sendResponseHeaders(200, 0);
            try (var output = exchange.getResponseBody()) {
                for (int i = 0; i < 30; i++) {
                    output.write(new byte[] {(byte) i});
                    output.flush();
                    Thread.sleep(15);
                }
            } catch (IOException ignored) {
                // The gateway closes the upstream stream on revocation.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        upstream.start();
        try {
            String studyRef = "opaque-study";
            when(scopes.resolve(eq("/studies/1.2.3"), anyMap()))
                    .thenReturn(Optional.of(new ImagingDicomWebScopeResolver.StudyScope(studyRef, "1.2.3")));
            when(grants.isAuthorized(eq(studyRef), eq("opaque-token")))
                    .thenAnswer(invocation -> checks.incrementAndGet() == 1);
            ImagingProperties properties = new ImagingProperties();
            properties.setOrthancBaseUri(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
            var controller = new ImagingDicomWebGatewayController(properties, scopes, grants, Duration.ofMillis(40));
            var request = new MockHttpServletRequest("GET", "/dicom-web/studies/1.2.3");
            request.setCookies(new jakarta.servlet.http.Cookie(
                    ImagingViewerGrantService.cookieName(studyRef), "opaque-token"));
            var response = new MockHttpServletResponse();

            controller.proxy(request, response);

            assertTrue(checks.get() >= 2);
            assertTrue(response.getContentAsByteArray().length > 0);
            assertTrue(response.getContentAsByteArray().length < 30);
        } finally {
            upstream.stop(0);
        }
    }

    @Test
    void rejectsMetadataContainingAReferenceOutsideTheAuthorizedStudy() throws Exception {
        String studyRef = "opaque-study";
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = ("[{\"7fe00010\":{\"BulkDataURI\":\"http://127.0.0.1:"
                + upstream.getAddress().getPort()
                + "/dicom-web/studies/9.9/series/9.8/instances/9.7/bulkdata/7fe00010\"}}]")
                .getBytes(StandardCharsets.UTF_8);
        upstream.createContext("/dicom-web/studies/1.2.3/metadata", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/dicom+json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        try {
            when(scopes.resolve(eq("/studies/1.2.3/metadata"), anyMap()))
                    .thenReturn(Optional.of(new ImagingDicomWebScopeResolver.StudyScope(studyRef, "1.2.3")));
            when(scopes.resolve(eq("/studies/9.9/series/9.8/instances/9.7/bulkdata/7fe00010"), anyMap()))
                    .thenReturn(Optional.empty());
            when(grants.isAuthorized(eq(studyRef), eq("opaque-token"))).thenReturn(true);
            ImagingProperties properties = new ImagingProperties();
            properties.setOrthancBaseUri(URI.create("http://127.0.0.1:" + upstream.getAddress().getPort()));
            var controller = new ImagingDicomWebGatewayController(properties, scopes, grants);
            var request = new MockHttpServletRequest("GET", "/dicom-web/studies/1.2.3/metadata");
            request.setCookies(new jakarta.servlet.http.Cookie(
                    ImagingViewerGrantService.cookieName(studyRef), "opaque-token"));
            var response = new MockHttpServletResponse();

            controller.proxy(request, response);

            assertEquals(502, response.getStatus());
            assertTrue(response.getContentAsString().contains("request_denied"));
            assertTrue(!response.getContentAsString().contains("9.9"));
        } finally {
            upstream.stop(0);
        }
    }

    private static ImagingProperties settings() {
        ImagingProperties properties = new ImagingProperties();
        properties.setOrthancBaseUri(URI.create("http://127.0.0.1:8042"));
        return properties;
    }
}
