package fu.fa26.sep490.g58.otcms_be.visit;

import fu.fa26.sep490.g58.otcms_be.visit.config.VisitCareProperties;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitRoomCapacityConflictException;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomReservationRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.VisitRoomCapacityServiceImpl;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VisitRoomCapacityT031Test {
    private static final Instant START = Instant.parse("2026-10-19T10:00:00Z");
    private static final Instant END = START.plusSeconds(1800);
    private final VisitRoomReservationRepository reservations = mock(VisitRoomReservationRepository.class);
    private final VisitCareProperties properties = new VisitCareProperties();
    private final VisitRoomCapacityServiceImpl service = new VisitRoomCapacityServiceImpl(properties, reservations);

    @Test
    void missingRoomCapabilityMappingFailsClosed() {
        VisitOperationException failure = assertThrows(VisitOperationException.class,
                () -> service.prepare("CONSULTATION", START, END, null));

        assertEquals("VISIT_CARE_STEP_NOT_CONFIGURED", failure.code());
        verify(reservations, never()).findAvailableRoom(any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void explicitNoneDoesNotQueryRoomInventory() {
        properties.setCareStepRoomCapability(Map.of("CONSULTATION", "NONE"));

        var plan = service.prepare("CONSULTATION", START, END, null);

        assertNull(plan.roomId());
        assertEquals("NONE", plan.capabilityCode());
        verify(reservations, never()).findAvailableRoom(any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void missingCleanupBufferFailsClosedBeforeRoomLookup() {
        properties.setCareStepRoomCapability(Map.of("CONSULTATION", "EXAM_ROOM"));

        VisitOperationException failure = assertThrows(VisitOperationException.class,
                () -> service.prepare("CONSULTATION", START, END, null));

        assertEquals("VISIT_CARE_STEP_NOT_CONFIGURED", failure.code());
        verify(reservations, never()).findAvailableRoom(any(), any(), any(), any(), any(), anyInt(), any());
    }

    @Test
    void configuredCapabilityWithoutAvailableRoomIsCapacityConflict() {
        properties.setCareStepRoomCapability(Map.of("CONSULTATION", "EXAM_ROOM"));
        properties.setRoomCleanupBufferMinutes(Map.of("EXAM_ROOM", 5));
        when(reservations.findAvailableRoom("CONSULTATION", "EXAM_ROOM", START, END,
                END.plusSeconds(300), 5, null)).thenReturn(null);

        assertThrows(VisitRoomCapacityConflictException.class,
                () -> service.prepare("CONSULTATION", START, END, null));
        verify(reservations).findAvailableRoom("CONSULTATION", "EXAM_ROOM", START, END,
                END.plusSeconds(300), 5, null);
    }
}
