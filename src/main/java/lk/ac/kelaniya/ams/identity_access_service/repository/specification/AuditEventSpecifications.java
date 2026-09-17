package lk.ac.kelaniya.ams.identity_access_service.repository.specification;

import jakarta.persistence.criteria.Predicate;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEvent;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Specifications for dynamic querying and filtering of AuditEvent entities.
 */
public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    /**
     * Constructs a composite specification for filtering audit events by eventType,
     * subjectUserId, actorUserId, and created date range.
     *
     * @param eventType     filter by specific audit event type
     * @param subjectUserId filter by subject user identifier
     * @param actorUserId   filter by actor user identifier
     * @param from          filter records created at or after this timestamp
     * @param to            filter records created at or before this timestamp
     * @return JPA Specification combining all non-null criteria
     */
    public static Specification<AuditEvent> withFilters(
            AuditEventType eventType,
            UUID subjectUserId,
            UUID actorUserId,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (eventType != null) {
                predicates.add(cb.equal(root.get("eventType"), eventType));
            }

            if (subjectUserId != null) {
                predicates.add(cb.equal(root.get("subjectUserId"), subjectUserId));
            }

            if (actorUserId != null) {
                predicates.add(cb.equal(root.get("actorUserId"), actorUserId));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
