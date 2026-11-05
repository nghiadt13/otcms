package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.UUID;

/** Internal UC38 registration. Requires the caller's local database transaction; no HTTP or price inputs. */
public interface DiagnosticImagingRequestRegistration {
    RegistrationResult register(RegisterImagingOrder command);

    record RegisterImagingOrder(UUID serviceOrderId, UUID visitId, UUID patientId,
            UUID orderingDoctorProfileId, UUID serviceId, String bodyArea, String clinicalReason) { }
    record RegistrationResult(UUID requestId) { }
}
