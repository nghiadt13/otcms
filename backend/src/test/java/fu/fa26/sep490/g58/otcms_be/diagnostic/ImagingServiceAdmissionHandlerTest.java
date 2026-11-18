package fu.fa26.sep490.g58.otcms_be.diagnostic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration.RegisterImagingOrder;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl.ImagingServiceAdmissionHandler;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService.QueueRoom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImagingServiceAdmissionHandlerTest {
    private final ClinicalOrderingService ordering = mock(ClinicalOrderingService.class);
    private final ImagingRoomQueueService rooms = mock(ImagingRoomQueueService.class);
    private final DiagnosticImagingRequestRegistration registration = mock(DiagnosticImagingRequestRegistration.class);
    private final ImagingServiceAdmissionHandler handler = new ImagingServiceAdmissionHandler(ordering, rooms, registration);
    private final UUID orderId = UUID.randomUUID(), serviceId = UUID.randomUUID(), visit = UUID.randomUUID(),
            patient = UUID.randomUUID(), doctor = UUID.randomUUID();

    private ClinicalOrderingService.OrderView order() {
        return new ClinicalOrderingService.OrderView(orderId, "O-1", visit, patient, UUID.randomUUID(), serviceId, "X-ray knee",
                fu.fa26.sep490.g58.otcms_be.clinical.model.ClinicalServiceType.IMAGING, doctor, "Pain", "Knee", "No metal",
                java.math.BigDecimal.TEN, fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderStatus.ORDERED,
                fu.fa26.sep490.g58.otcms_be.clinical.model.ServiceOrderPaymentStatus.PAID);
    }

    @Test
    void supportsOnlyImaging() {
        assertTrue(handler.supports("IMAGING"));
        assertFalse(handler.supports("LAB"));
    }

    @Test
    void offersTheCatalogRoomsOfTheOrderService() {
        UUID roomA = UUID.randomUUID(), roomB = UUID.randomUUID();
        when(ordering.getImagingOrder(orderId)).thenReturn(order());
        when(rooms.findEligibleRooms(List.of(serviceId))).thenReturn(Map.of(serviceId,
                List.of(new QueueRoom(roomA, "A", "A"), new QueueRoom(roomB, "B", "B"))));
        assertEquals(List.of(roomA, roomB), handler.eligibleRoomIds(orderId));
    }

    @Test
    void assignRoomRegistersThePendingImagingRequestInThatRoom() {
        UUID room = UUID.randomUUID();
        when(ordering.getImagingOrder(orderId)).thenReturn(order());
        handler.assignRoom(orderId, room);
        var captor = ArgumentCaptor.forClass(RegisterImagingOrder.class);
        verify(registration).register(captor.capture());
        assertEquals(room, captor.getValue().roomId());
        assertEquals(doctor, captor.getValue().orderingDoctorProfileId());
        assertEquals("Knee", captor.getValue().bodyArea());
    }
}
