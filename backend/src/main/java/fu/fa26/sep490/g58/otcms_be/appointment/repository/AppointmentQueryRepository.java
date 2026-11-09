package fu.fa26.sep490.g58.otcms_be.appointment.repository;

import fu.fa26.sep490.g58.otcms_be.appointment.model.entity.AppointmentJpaEntity;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Filter;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Page;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Row;
import fu.fa26.sep490.g58.otcms_be.appointment.model.Scope;
import fu.fa26.sep490.g58.otcms_be.appointment.model.ScopeType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentQueryRepository extends JpaRepository<AppointmentJpaEntity, UUID>,
        JpaSpecificationExecutor<AppointmentJpaEntity> {
    default Page find(Scope scope, Filter filter, int page, int size) {
        Sort.Direction direction = "UPCOMING".equals(filter.patientWindow())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        var result = findAll(criteria(scope, filter, null), PageRequest.of(page, size,
                Sort.by(new Sort.Order(direction, "startTime"), new Sort.Order(direction, "id"))));
        return new Page(result.stream().map(AppointmentQueryRepository::row).toList(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    default Optional<Row> findScoped(Scope scope, UUID id) {
        return findOne(criteria(scope, new Filter(null, null, null, null, null), id))
                .map(AppointmentQueryRepository::row);
    }

    /**
     * Technical hold states that no list, report or staff screen may ever show. Only the booking patient reads one
     * (by id, to follow the payment on the step-5 screen).
     */
    List<String> PAST_STATUSES = List.of("CHECKED_IN", "IN_PROGRESS", "COMPLETED", "CANCELLED", "NO_SHOW");
    List<String> HIDDEN_HOLD_STATUSES = List.of("AWAITING_PAYMENT", "PAYMENT_EXPIRED");

    private static Specification<AppointmentJpaEntity> criteria(Scope scope, Filter filter, UUID id) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            // Lists never return a hold; a single-id lookup returns it to the patient scope only.
            if (id == null || scope.type() != ScopeType.PATIENT) {
                predicates.add(builder.not(root.get("status").in(HIDDEN_HOLD_STATUSES)));
            }
            if (id != null) predicates.add(builder.equal(root.get("id"), id));
            if (scope.type() == ScopeType.PATIENT) predicates.add(root.get("patientProfileId").in(scope.profileIds()));
            if (scope.type() == ScopeType.DOCTOR) predicates.add(builder.equal(root.get("doctorProfileId"), scope.profileId()));
            if (filter.fromInclusive() != null) predicates.add(builder.greaterThanOrEqualTo(root.get("startTime"), filter.fromInclusive()));
            if (filter.toExclusive() != null) predicates.add(builder.lessThan(root.get("startTime"), filter.toExclusive()));
            if (filter.status() != null) predicates.add(builder.equal(root.get("status"), filter.status()));
            if (filter.patientWindow() != null && filter.evaluatedAt() != null) {
                if ("UPCOMING".equals(filter.patientWindow())) {
                    predicates.add(builder.equal(root.get("status"), "CONFIRMED"));
                    predicates.add(builder.greaterThan(root.get("endTime"), filter.evaluatedAt()));
                } else if ("PAST".equals(filter.patientWindow())) {
                    predicates.add(builder.or(
                            builder.lessThanOrEqualTo(root.get("endTime"), filter.evaluatedAt()),
                            root.get("status").in(PAST_STATUSES)));
                }
            }
            if (filter.doctorId() != null) predicates.add(builder.equal(root.get("doctorProfileId"), filter.doctorId()));
            if (filter.patientProfileId() != null) predicates.add(builder.equal(root.get("patientProfileId"), filter.patientProfileId()));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static Row row(AppointmentJpaEntity entity) {
        return new Row(entity.getId(), entity.getPatientProfileId(), entity.getServiceId(),
                entity.getDoctorProfileId(), entity.getStatus(), entity.getBookingSource(), entity.getPurpose(),
                entity.getStartTime(), entity.getEndTime(), entity.getDurationMinutes(), entity.getBufferMinutes(),
                entity.getOccupiedUntil(), entity.getVisitId(), entity.getSourceVisitId(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getPaymentDeadline());
    }
}
