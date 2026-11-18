package fu.fa26.sep490.g58.otcms_be.diagnostic.service.impl;

import fu.fa26.sep490.g58.otcms_be.clinical.service.ClinicalOrderingService;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration;
import fu.fa26.sep490.g58.otcms_be.diagnostic.service.DiagnosticImagingRequestRegistration.RegisterImagingOrder;
import fu.fa26.sep490.g58.otcms_be.visit.service.ImagingRoomQueueService;
import fu.fa26.sep490.g58.otcms_be.visit.service.ServiceAdmissionHandler;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Imaging part of room admission: eligible rooms come from the room catalog, the room is stored as a PENDING imaging request. */
@Component
public class ImagingServiceAdmissionHandler implements ServiceAdmissionHandler {
    private final ClinicalOrderingService ordering;
    private final ImagingRoomQueueService rooms;
    private final DiagnosticImagingRequestRegistration registration;

    public ImagingServiceAdmissionHandler(ClinicalOrderingService ordering, ImagingRoomQueueService rooms,
            DiagnosticImagingRequestRegistration registration) {
        this.ordering = ordering;
        this.rooms = rooms;
        this.registration = registration;
    }

    @Override
    public boolean supports(String serviceType) { return "IMAGING".equals(serviceType); }

    @Override
    public List<UUID> eligibleRoomIds(UUID serviceOrderId) {
        UUID serviceId = ordering.getImagingOrder(serviceOrderId).serviceId();
        return rooms.findEligibleRooms(List.of(serviceId)).getOrDefault(serviceId, List.of()).stream()
                .map(ImagingRoomQueueService.QueueRoom::roomId).toList();
    }

    @Override
    public void assignRoom(UUID serviceOrderId, UUID roomId) {
        var order = ordering.getImagingOrder(serviceOrderId);
        registration.register(new RegisterImagingOrder(order.orderId(), order.visitId(), order.patientId(),
                order.requestingDoctorId(), order.serviceId(), roomId, order.bodyArea(), order.clinicalReason()));
    }
}
