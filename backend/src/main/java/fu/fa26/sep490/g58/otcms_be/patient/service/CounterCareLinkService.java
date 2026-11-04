package fu.fa26.sep490.g58.otcms_be.patient.service;

import fu.fa26.sep490.g58.otcms_be.patient.model.dto.DependentDtos.*;
import java.util.UUID;

public interface CounterCareLinkService {
    Items<CounterItem> pending(UUID actor, String query);
    LinkResult confirmCounter(UUID actor, UUID linkId, Boolean identityChecked);
    void rejectCounter(UUID actor, UUID linkId);
}
