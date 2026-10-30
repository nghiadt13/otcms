package fu.fa26.sep490.g58.otcms_be.visit.service.impl;

import fu.fa26.sep490.g58.otcms_be.auth.service.ReceptionistAccountAccessService;
import fu.fa26.sep490.g58.otcms_be.common.security.AccountRole;
import fu.fa26.sep490.g58.otcms_be.common.security.AuthenticatedAccount;
import fu.fa26.sep490.g58.otcms_be.config.AppointmentBookingProperties;
import fu.fa26.sep490.g58.otcms_be.patient.exception.PatientAuthorizationDeniedException;
import fu.fa26.sep490.g58.otcms_be.patient.service.AuthorizePatientProfileService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionQueueItemResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.ReceptionQueueResponse;
import fu.fa26.sep490.g58.otcms_be.visit.model.dto.response.VisitProgressResponse;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitReceptionReadRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.VisitReceptionReadService;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VisitReceptionReadServiceImpl implements VisitReceptionReadService {
    private static final int MAX_PAGE_SIZE = 100;
    private final VisitReceptionReadRepository reads;
    private final ReceptionistAccountAccessService receptionists;
    private final AuthorizePatientProfileService patientAuthorization;
    private final AppointmentBookingProperties booking;

    public VisitReceptionReadServiceImpl(VisitReceptionReadRepository reads,
            ReceptionistAccountAccessService receptionists,
            AuthorizePatientProfileService patientAuthorization, AppointmentBookingProperties booking) {
        this.reads = reads;
        this.receptionists = receptionists;
        this.patientAuthorization = patientAuthorization;
        this.booking = booking;
    }

    @Override
    @Transactional(readOnly = true)
    public ReceptionQueueResponse listWalkInQueue(UUID receptionistAccountId, LocalDate date, int page, int size) {
        if (date == null || page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedAccount principal)
                || principal.accountRole() != AccountRole.RECEPTIONIST
                || receptionistAccountId == null || !receptionistAccountId.equals(principal.accountId())
                || !receptionists.isActiveReceptionistAccount(receptionistAccountId)) {
            throw new VisitOperationException("ACCESS_DENIED");
        }
        int offset;
        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new VisitOperationException("VALIDATION_FAILED");
        }
        Timestamp from = Timestamp.from(date.atStartOfDay(booking.clinicTimeZone()).toInstant());
        Timestamp to = Timestamp.from(nextClinicDay(date).atStartOfDay(booking.clinicTimeZone()).toInstant());
        List<Object[]> rows = reads.findQueue(from, to, offset, size + 1);
        List<ReceptionQueueItemResponse> items = rows.stream()
                .limit(size).map(row -> new ReceptionQueueItemResponse((UUID) row[0], (UUID) row[1],
                        instant(row[2]), (String) row[3], ((Number) row[4]).intValue(), (String) row[5],
                        (String) row[6])).toList();
        return new ReceptionQueueResponse(date, page, size, rows.size() > size, items);
    }

    @Override
    @Transactional(readOnly = true)
    public VisitProgressResponse getOwnProgress(UUID patientAccountId, UUID visitId) {
        if (patientAccountId == null || visitId == null) throw new VisitOperationException("VALIDATION_FAILED");
        Object[] row = reads.findProgress(visitId);
        if (row == null) throw new VisitOperationException("RESOURCE_NOT_FOUND");
        try {
            patientAuthorization.authorize(patientAccountId, (UUID) row[1]);
        } catch (PatientAuthorizationDeniedException exception) {
            throw new VisitOperationException("ACCESS_DENIED", exception);
        }
        boolean activeWalkInEntry = "WALK_IN".equals(row[2]) && row[6] != null && "WAITING".equals(row[7]);
        Integer position = null;
        if (activeWalkInEntry) {
            LocalDate clinicDate = instant(row[4]).atZone(booking.clinicTimeZone()).toLocalDate();
            Timestamp from = Timestamp.from(clinicDate.atStartOfDay(booking.clinicTimeZone()).toInstant());
            Timestamp to = Timestamp.from(nextClinicDay(clinicDate).atStartOfDay(booking.clinicTimeZone()).toInstant());
            int currentPosition = reads.findPosition(asUuid(row[6]), from, to);
            if (currentPosition > 0) position = currentPosition;
        }
        return new VisitProgressResponse((UUID) row[0], (String) row[2], (String) row[3],
                (String) row[5], instant(row[4]), position, (String) row[8]);
    }

    private static java.time.Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof java.time.Instant instant) return instant;
        if (value instanceof java.time.OffsetDateTime dateTime) return dateTime.toInstant();
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        throw new IllegalStateException("Unexpected database timestamp type");
    }

    private static UUID asUuid(Object value) {
        if (value instanceof UUID id) return id;
        if (value instanceof String id) return UUID.fromString(id);
        throw new IllegalStateException("Unexpected database UUID type");
    }

    private static LocalDate nextClinicDay(LocalDate date) {
        try {
            return date.plusDays(1);
        } catch (java.time.DateTimeException exception) {
            throw new VisitOperationException("VALIDATION_FAILED", exception);
        }
    }
}
