package fu.fa26.sep490.g58.otcms_be.auth.service.impl;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TemporaryPasswordGeneratorTest {
    @Test void regeneratesInsteadOfModifyingRandomBytesOrWeakeningTheCandidate() {
        SecureRandom random = mock(SecureRandom.class);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            byte[] bytes = invocation.getArgument(0);
            assertEquals(32, bytes.length);
            if (calls.getAndIncrement() == 0) Arrays.fill(bytes, (byte) 0);
            else for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
            return null;
        }).when(random).nextBytes(any(byte[].class));
        String result = new TemporaryPasswordGenerator(random).generate();
        assertTrue(PasswordPolicy.isValid(result));
        assertEquals(43, result.length());
        verify(random, times(2)).nextBytes(any(byte[].class));
    }
    @Test void generatedCredentialsAlwaysMeetSharedPolicy() {
        var generator = new TemporaryPasswordGenerator();
        for (int i = 0; i < 100; i++) assertTrue(PasswordPolicy.isValid(generator.generate()));
    }
}
