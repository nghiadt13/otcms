package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import java.util.UUID;

/** UC13: confirm a dependent-add request with an email OTP sent to the requester's own email. */
public interface DependentAddOtpService {
    /** Validate the request, store it and email an OTP to the requester's account. */
    AddOtpResult request(UUID actor, AddDependentRequest request);
    /** Replace the code: the open request is superseded by a new one with the same details. */
    AddOtpResult resend(UUID actor, UUID requestId);
    /** Check the code, then run the normal add-dependent flow with the stored details. */
    LinkResult verify(UUID actor, UUID requestId, String code);
}
