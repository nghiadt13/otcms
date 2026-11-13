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
    void conflictUsesStableKeyAndRestoresCorrelationContext() {
        var writer = mock(fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter.class);
        var repository = new VisitRoomReservationRepository(mock(jakarta.persistence.EntityManager.class), writer);
        var visit = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001");
        var actor = java.util.UUID.fromString("00000000-0000-0000-0000-000000000002");
        String mdcKey = fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.MDC_KEY;
        org.slf4j.MDC.put(mdcKey, "outer");
        try {
            when(writer.recordIfAbsent(any())).thenAnswer(call -> {
                assertEquals("request-1", org.slf4j.MDC.get(mdcKey));
                return true;
            });
            repository.appendConflict(visit, null, actor, "CONSULTATION", "request-1", START);
            var entry = org.mockito.ArgumentCaptor.forClass(fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry.class);
            verify(writer).recordIfAbsent(entry.capture());
            assertEquals("room-capacity:86ec6f01d47cd75aa805b4de43372a3e1741d8186a2c271ec58df8618f6f58b2", entry.getValue().dedupKey());
            assertEquals("outer", org.slf4j.MDC.get(mdcKey));
            assertThrows(NullPointerException.class, () -> repository.appendConflict(visit, null, actor, "CONSULTATION", null, START));
            assertThrows(NullPointerException.class, () -> repository.appendConflict(visit, null, null, "CONSULTATION", "request-1", START));
        } finally { org.slf4j.MDC.remove(mdcKey); }
    }

    @Test
    void roomAuditUsesAndRestoresExplicitCorrelation() {
        var guard = mock(fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService.class);
        var rooms = mock(fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository.class);
        var catalog = mock(fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService.class);
        var writer = mock(fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter.class);
        when(rooms.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(rooms.snapshot(any())).thenReturn("{\"roomCode\":\"TEST\"}");
        var roomService = new fu.fa26.sep490.g58.otcms_be.visit.service.impl.ManageVisitRoomServiceImpl(
                guard, rooms, catalog, java.time.Clock.fixed(START, java.time.ZoneOffset.UTC), writer,
                new tools.jackson.databind.ObjectMapper());
        String mdcKey = fu.fa26.sep490.g58.otcms_be.common.web.CorrelationIdFilter.MDC_KEY;
        org.slf4j.MDC.put(mdcKey, "outer");
        try {
            org.mockito.Mockito.doAnswer(call -> { assertEquals("request-2", org.slf4j.MDC.get(mdcKey)); return null; })
                    .when(writer).record(any());
            roomService.create("TEST", "Test", null, null, null, java.util.List.of("EXAM_ROOM"), java.util.UUID.randomUUID(), "request-2");
            var entry = org.mockito.ArgumentCaptor.forClass(fu.fa26.sep490.g58.otcms_be.common.audit.AuditEntry.class);
            verify(writer).record(entry.capture());
            assertEquals(START, entry.getValue().occurredAt());
            assertEquals("outer", org.slf4j.MDC.get(mdcKey));
        } finally { org.slf4j.MDC.remove(mdcKey); }
    }

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
