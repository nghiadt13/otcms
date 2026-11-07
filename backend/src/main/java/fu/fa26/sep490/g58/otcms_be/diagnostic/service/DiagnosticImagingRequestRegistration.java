package fu.fa26.sep490.g58.otcms_be.diagnostic.service;

import java.util.UUID;

/** Internal paid Clinical order registration for Reception dispatch. Requires the caller's local transaction; no HTTP or price inputs. */
public interface DiagnosticImagingRequestRegistration {
    RegistrationResult register(RegisterImagingOrder command);

    record RegisterImagingOrder(UUID serviceOrderId, UUID visitId, UUID patientId,
            UUID orderingDoctorProfileId, UUID serviceId, UUID roomId, String bodyArea, String clinicalReason) { }
    record RegistrationResult(UUID requestId) { }
}
