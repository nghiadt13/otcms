package fu.fa26.sep490.g58.otcms_be.appointment.service.impl;

import fu.fa26.sep490.g58.otcms_be.appointment.exception.AppointmentBookingException;
import fu.fa26.sep490.g58.otcms_be.appointment.service.ClinicArrivalProofService;
import fu.fa26.sep490.g58.otcms_be.config.ArrivalQrProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class ClinicArrivalProofServiceImpl implements ClinicArrivalProofService {
    private final ArrivalQrProperties properties;
    private final Clock clock;

    public ClinicArrivalProofServiceImpl(ArrivalQrProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public ArrivalProof issue() {
        Instant now = Instant.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Instant expires = now.plusSeconds(properties.ttlSeconds());
        String payload = "v1.k1." + enc(properties.clinicId()) + "." + now.getEpochSecond() + "."
                + expires.getEpochSecond() + "." + UUID.randomUUID();
        return new ArrivalProof(payload + "." + enc(sign(payload)), now, expires, properties.refreshSeconds());
    }

    @Override
    public void validate(String proof, Instant now) {
        try {
            String[] parts = proof == null ? new String[0] : proof.split("\\.", -1);
            if (parts.length != 7 || !"v1".equals(parts[0]) || !"k1".equals(parts[1])
                    || !properties.clinicId().equals(dec(parts[2]))) throw invalid();
            long issued = Long.parseLong(parts[3]);
            long expires = Long.parseLong(parts[4]);
            UUID.fromString(parts[5]);
            byte[] supplied = Base64.getUrlDecoder().decode(parts[6]);
            String signed = String.join(".", parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
            if (!MessageDigest.isEqual(sign(signed), supplied) || expires - issued != properties.ttlSeconds()
                    || issued > now.getEpochSecond() || expires <= now.getEpochSecond()) throw invalid();
        } catch (AppointmentBookingException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            throw new AppointmentBookingException("ARRIVAL_PROOF_UNAVAILABLE", exception);
        } catch (RuntimeException exception) {
            throw invalid();
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.requiredSecret(), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (AppointmentBookingException exception) {
            throw exception;
        } catch (IllegalStateException exception) {
            throw new AppointmentBookingException("ARRIVAL_PROOF_UNAVAILABLE", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Arrival proof signing is unavailable", exception);
        }
    }

    private static String enc(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String dec(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static AppointmentBookingException invalid() {
        return new AppointmentBookingException("ARRIVAL_PROOF_INVALID");
    }
}
