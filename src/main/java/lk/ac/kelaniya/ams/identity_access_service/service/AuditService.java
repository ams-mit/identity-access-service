package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.response.AuditEventResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEvent;
import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEventType;
import lk.ac.kelaniya.ams.identity_access_service.repository.AuditEventRepository;
import lk.ac.kelaniya.ams.identity_access_service.repository.specification.AuditEventSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service managing persistent audit event recording and querying.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    /**
     * Persists an audit event record to the unified audit log.
     * Uses REQUIRED propagation so it executes reliably within the current transaction context.
     *
     * @param type          categorized audit event type
     * @param subjectUserId ID of user the event is about (target)
     * @param actorUserId   ID of user who performed the action (null for self-service or unauthenticated events)
     * @param oldValue      previous value prior to change
     * @param newValue      new value following change
     * @param reason        justification or diagnostic reason
     * @return persisted AuditEvent entity
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditEvent record(
            AuditEventType type,
            UUID subjectUserId,
            UUID actorUserId,
            String oldValue,
            String newValue,
            String reason
    ) {
        log.debug("Recording audit event: type={}, subjectUserId={}, actorUserId={}", type, subjectUserId, actorUserId);

        AuditEvent event = AuditEvent.builder()
                .eventType(type)
                .subjectUserId(subjectUserId)
                .actorUserId(actorUserId)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .build();

        return auditEventRepository.save(event);
    }

    /**
     * Searches and paginates audit log records matching the specified criteria.
     *
     * @param eventType     filter by audit event type
     * @param subjectUserId filter by target subject user ID
     * @param actorUserId   filter by actor user ID
     * @param from          start of created timestamp range (inclusive)
     * @param to            end of created timestamp range (inclusive)
     * @param pageable      pagination and sorting arguments
     * @return paginated response containing mapped audit event records
     */
    @Transactional(readOnly = true)
    public Page<AuditEventResponse> searchAuditLogs(
            AuditEventType eventType,
            UUID subjectUserId,
            UUID actorUserId,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Specification<AuditEvent> spec = AuditEventSpecifications.withFilters(
                eventType, subjectUserId, actorUserId, from, to
        );

        return auditEventRepository.findAll(spec, pageable)
                .map(AuditEventResponse::fromEntity);
    }
}
