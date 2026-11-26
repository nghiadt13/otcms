package fu.fa26.sep490.g58.otcms_be.clinical.event;

import java.util.UUID;

/** Published when a doctor submits a clinical ordering round so billing can open its invoice immediately. */
public record ClinicalOrderingRoundCreatedEvent(UUID roundId, UUID orderedByDoctorProfileId) { }
