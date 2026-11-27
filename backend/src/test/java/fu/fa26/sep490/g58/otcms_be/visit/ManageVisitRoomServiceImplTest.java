package fu.fa26.sep490.g58.otcms_be.visit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyBoolean;

import fu.fa26.sep490.g58.otcms_be.common.audit.AuditLogWriter;
import fu.fa26.sep490.g58.otcms_be.common.service.CoordinationGuardService;
import fu.fa26.sep490.g58.otcms_be.servicecatalog.service.GetClinicServiceCatalogService;
import fu.fa26.sep490.g58.otcms_be.visit.exception.VisitOperationException;
import fu.fa26.sep490.g58.otcms_be.visit.model.VisitRoomType;
import fu.fa26.sep490.g58.otcms_be.visit.model.entity.VisitRoomJpaEntity;
import fu.fa26.sep490.g58.otcms_be.visit.repository.VisitRoomJpaRepository;
import fu.fa26.sep490.g58.otcms_be.visit.service.impl.ManageVisitRoomServiceImpl;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ManageVisitRoomServiceImplTest {
    private final CoordinationGuardService guard = mock(CoordinationGuardService.class);
    private final VisitRoomJpaRepository rooms = mock(VisitRoomJpaRepository.class);
    private final GetClinicServiceCatalogService catalog = mock(GetClinicServiceCatalogService.class);
    private final AuditLogWriter auditLog = mock(AuditLogWriter.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC);
    private final ManageVisitRoomServiceImpl service = new ManageVisitRoomServiceImpl(
            guard, rooms, catalog, clock, auditLog, JsonMapper.builder().build());
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void configureRepository() {
        when(rooms.saveAndFlush(any(VisitRoomJpaEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(rooms.snapshot(any())).thenReturn("{\"roomCode\":\"FRONT-DESK\"}");
    }

    @Test
    void createsReceptionDeskWithoutClinicServices() {
        when(rooms.existsByRoomCode("FRONT-DESK")).thenReturn(false);

        var room = service.create("front-desk", "Front desk", VisitRoomType.RECEPTION,
                null, null, null, actorId, "correlation-1");

        assertEquals("RECEPTION", room.type());
        assertEquals(List.of("RECEPTION_DESK"), room.capabilities());
        assertEquals(List.of(), room.supportedServiceIds());
        verify(catalog, never()).roomOptions(any(), anyBoolean());
        verify(auditLog).record(any());
    }

    @Test
    void rejectsClinicServicesForReceptionDesk() {
        UUID clinicServiceId = UUID.randomUUID();

        assertThrows(VisitOperationException.class, () -> service.create("front-desk", "Front desk",
                VisitRoomType.RECEPTION, null, List.of(clinicServiceId), null, actorId, "correlation-2"));

        verify(rooms, never()).saveAndFlush(any());
        verify(catalog, never()).roomOptions(any(), anyBoolean());
    }
}
