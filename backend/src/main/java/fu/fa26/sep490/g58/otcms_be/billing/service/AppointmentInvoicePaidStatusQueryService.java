package fu.fa26.sep490.g58.otcms_be.billing.service;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Read-only payment status lookup for appointment projections. */
public interface AppointmentInvoicePaidStatusQueryService {
    Map<UUID, Boolean> paidStatus(Collection<UUID> appointmentIds);
}
