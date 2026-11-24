package fu.fa26.sep490.g58.otcms_be.appointment;

import fu.fa26.sep490.g58.otcms_be.appointment.service.impl.AppointmentRescheduleCountQueryServiceImpl;
import fu.fa26.sep490.g58.otcms_be.common.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppointmentRescheduleCountQueryServiceImplTest {
    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final AppointmentRescheduleCountQueryServiceImpl service =
            new AppointmentRescheduleCountQueryServiceImpl(auditLogs);

    @Test
    void countsOnlyPatientRescheduledAuditEntries() {
        UUID appointmentId = UUID.randomUUID();
        when(auditLogs.countBySourceAndActionAndEntityIdAndActorType(
                "appointment_audits", "RESCHEDULED", appointmentId, "PATIENT"))
                .thenReturn(2L);

        assertEquals(2, service.countPatientReschedules(appointmentId));
        assertEquals(0, service.countPatientReschedules((UUID) null));
    }

    @Test
    void batchCountHandlesEmptyInputAndMapsRows() {
        assertEquals(Map.of(), service.countPatientReschedules(List.of()));
        verifyNoInteractions(auditLogs);

        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(auditLogs.countByEntityIdsAndSourceAndActionAndActorType(
                List.of(id1, id2), "appointment_audits", "RESCHEDULED", "PATIENT"))
                .thenReturn(List.<Object[]>of(new Object[]{id1, 1L}));

        assertEquals(Map.of(id1, 1), service.countPatientReschedules(List.of(id1, id2)));
    }
}
