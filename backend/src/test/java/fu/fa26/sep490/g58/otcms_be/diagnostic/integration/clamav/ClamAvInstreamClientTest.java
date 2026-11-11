package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.FutureTask;
import org.junit.jupiter.api.Test;

class ClamAvInstreamClientTest {
    @Test
    void sendsClamdInstreamFramesAndRecognizesCleanResponse() throws Exception {
        byte[] expected = new byte[] {9, 8, 7, 6, 5};
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<byte[]> received = new FutureTask<>(() -> receiveAndReply(server));
            Thread worker = new Thread(received, "clamav-test-server");
            worker.setDaemon(true);
            worker.start();

            var scanner = new ClamAvInstreamClient("127.0.0.1", server.getLocalPort(), 1000, 1000);
            assertEquals(ClamAvScanner.ScanResult.CLEAN, scanner.scan(expected));
            assertArrayEquals(expected, received.get());
        }
    }

    @Test
    void reportsUnknownScannerReplyAsUnavailable() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            FutureTask<Void> responder = new FutureTask<>(() -> {
                try (Socket socket = server.accept()) {
                    drainStream(socket);
                    socket.getOutputStream().write("stream: INSTREAM size limit exceeded\0"
                            .getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                }
                return null;
            });
            Thread worker = new Thread(responder, "clamav-test-server");
            worker.setDaemon(true);
            worker.start();

            var scanner = new ClamAvInstreamClient("127.0.0.1", server.getLocalPort(), 1000, 1000);
            assertEquals(ClamAvScanner.ScanResult.UNAVAILABLE, scanner.scan(new byte[] {1}));
            responder.get();
        }
    }

    private byte[] receiveAndReply(ServerSocket server) throws Exception {
        try (Socket socket = server.accept()) {
            DataInputStream input = new DataInputStream(socket.getInputStream());
            byte[] command = input.readNBytes("zINSTREAM\0".length());
            assertEquals("zINSTREAM\0", new String(command, StandardCharsets.US_ASCII));
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            int length;
            while ((length = input.readInt()) > 0) content.write(input.readNBytes(length));
            socket.getOutputStream().write("stream: OK\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return content.toByteArray();
        }
    }

    private void drainStream(Socket socket) throws Exception {
        DataInputStream input = new DataInputStream(socket.getInputStream());
        input.readNBytes("zINSTREAM\0".length());
        int length;
        while ((length = input.readInt()) > 0) input.skipNBytes(length);
    }
}
