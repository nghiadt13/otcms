package fu.fa26.sep490.g58.otcms_be.diagnostic.integration.clamav;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Minimal bounded clamd INSTREAM client; it never logs scanner responses or document data. */
public final class ClamAvInstreamClient implements ClamAvScanner {
    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_RESPONSE_BYTES = 1024;

    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public ClamAvInstreamClient(String host, int port, int connectTimeoutMs, int readTimeoutMs) {
        this.host = host;
        this.port = port;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @Override
    public ScanResult scan(byte[] content) {
        if (content == null || content.length == 0) return ScanResult.UNAVAILABLE;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            var output = socket.getOutputStream();
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < content.length; offset += CHUNK_SIZE) {
                int length = Math.min(CHUNK_SIZE, content.length - offset);
                output.write(ByteBuffer.allocate(Integer.BYTES).order(ByteOrder.BIG_ENDIAN)
                        .putInt(length).array());
                output.write(content, offset, length);
            }
            output.write(new byte[Integer.BYTES]);
            output.flush();
            String response = readResponse(socket.getInputStream());
            if (response.endsWith(" OK")) return ScanResult.CLEAN;
            if (response.contains(" FOUND")) return ScanResult.INFECTED;
            return ScanResult.UNAVAILABLE;
        } catch (IOException | RuntimeException exception) {
            return ScanResult.UNAVAILABLE;
        }
    }

    private String readResponse(InputStream input) throws IOException {
        ByteArrayOutputStream response = new ByteArrayOutputStream();
        for (int i = 0; i < MAX_RESPONSE_BYTES; i++) {
            int value = input.read();
            if (value == -1 || value == 0 || value == '\n') break;
            response.write(value);
        }
        return response.toString(StandardCharsets.US_ASCII).trim();
    }
}
