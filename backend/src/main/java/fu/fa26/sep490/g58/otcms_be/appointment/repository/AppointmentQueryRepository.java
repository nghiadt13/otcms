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

    private static Specification<AppointmentJpaEntity> criteria(Scope scope, Filter filter, UUID id) {
        return (root, query, builder) -> {
            var predicates = new ArrayList<Predicate>();
            if (id != null) predicates.add(builder.equal(root.get("id"), id));
            if (scope.type() == ScopeType.PATIENT) predicates.add(builder.equal(root.get("patientProfileId"), scope.profileId()));
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
                            builder.notEqual(root.get("status"), "CONFIRMED")));
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
                entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
