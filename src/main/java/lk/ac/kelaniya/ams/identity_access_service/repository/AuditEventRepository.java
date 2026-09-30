package lk.ac.kelaniya.ams.identity_access_service.repository;

import lk.ac.kelaniya.ams.identity_access_service.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for AuditEvent entities.
 * Supports specification-based dynamic filtering and pagination.
 */
@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
