package fu.fa26.sep490.g58.otcms_be.appointment.service;

import java.time.Instant;

public interface ClinicArrivalProofService {
    ArrivalProof issue();
    void validate(String proof, Instant now);

    record ArrivalProof(String proof, Instant issuedAt, Instant expiresAt, long refreshAfterSeconds) { }
}
