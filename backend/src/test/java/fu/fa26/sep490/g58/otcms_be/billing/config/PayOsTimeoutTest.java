package fu.fa26.sep490.g58.otcms_be.billing.config;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.payos.PayOS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PayOS calls must give up quickly (settle queries PayOS); no real network: a local socket that never answers. */
class PayOsTimeoutTest {
    private static PayOsProperties properties(int timeoutMs) {
        return new PayOsProperties(true, "client", "key", "checksum", "http://r", "http://c", 900, timeoutMs);
    }

    @Test
    void timeoutDefaultsToFiveSecondsAndIsConfigurable() {
        assertEquals(5000, properties(0).timeoutMs());
        assertEquals(5000, properties(-1).timeoutMs());
        assertEquals(5000, PayOsConfiguration.clientOptions(properties(0)).build().getTimeoutMs());
        assertEquals(1234, PayOsConfiguration.clientOptions(properties(1234)).build().getTimeoutMs());
        assertEquals(0, PayOsConfiguration.clientOptions(properties(1234)).build().getMaxRetries());
    }

    @Test
    void queryAgainstAServerThatNeverRepliesFailsWithinTheConfiguredTimeout() throws Exception {
        List<Socket> held = new ArrayList<>();
        try (ServerSocket silent = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            Thread acceptor = Thread.ofVirtual().start(() -> {
                try { while (true) held.add(silent.accept()); } catch (Exception closed) { /* test finished */ }
            });
            var options = PayOsConfiguration.clientOptions(properties(400))
                    .baseURL("http://127.0.0.1:" + silent.getLocalPort()).build();
            PayOS payOS = new PayOS(options);
            try {
                long started = System.nanoTime();
                assertTimeoutPreemptively(Duration.ofSeconds(10),
                        () -> assertThrows(RuntimeException.class, () -> payOS.paymentRequests().get(123L)));
                long elapsedMs = (System.nanoTime() - started) / 1_000_000;
                assertTrue(elapsedMs < 5000, "PayOS call took " + elapsedMs + " ms");
            } finally {
                payOS.close();
            }
            acceptor.interrupt();
        } finally {
            for (Socket socket : held) socket.close();
        }
    }
}
