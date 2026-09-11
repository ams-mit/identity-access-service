package lk.ac.kelaniya.ams.identity_access_service.repository;

import lk.ac.kelaniya.ams.identity_access_service.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for Permission entities.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCode(String code);

    boolean existsByCode(String code);
}
