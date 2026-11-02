package fu.fa26.sep490.g58.otcms_be.appointment.service;

import fu.fa26.sep490.g58.otcms_be.appointment.model.AvailableSlot;
import fu.fa26.sep490.g58.otcms_be.appointment.model.SlotAvailabilityRequest;

import java.util.List;

public interface GenerateAvailableSlotsService {
    List<AvailableSlot> generate(SlotAvailabilityRequest request);
}
