package fu.fa26.sep490.g58.otcms_be.visit.service;

import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ExamQueueDtos;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Read side of the exam queue (plans/261111-0208-visit-flow-r3, section 3, A1-A3 and A6). */
public interface VisitExamQueueReadService {
    /** A1: every queue row of one clinic day, for reception. */
    ExamQueueDtos.ItemList listForReception(LocalDate clinicDate);

    /** A2 by doctor profile: today's rows plus unfinished rows of earlier days; no PENDING_PAYMENT or CANCELLED. */
    ExamQueueDtos.DoctorItemList listForDoctor(UUID doctorProfileId);

    /** A2 for the authenticated DOCTOR account; the profile is resolved here, and an inactive account is denied. */
    ExamQueueDtos.DoctorItemList listForDoctorAccount(AuthenticatedAccount actor);

    /** A3: doctors whose shift covers {@code now}, with their WAITING count for today. */
    ExamQueueDtos.OnShiftDoctorList onShiftDoctors(Instant now);

    /**
     * A6: one visit's queue row, in the A2 shape (with the patient's date of birth and gender). RECEPTIONIST (active),
     * the responsible DOCTOR, or the PATIENT who manages the profile. Missing row gives RESOURCE_NOT_FOUND; a denied
     * actor gives ACCESS_DENIED.
     */
    ExamQueueDtos.DoctorItem getEntry(UUID visitId, AuthenticatedAccount actor);
}
