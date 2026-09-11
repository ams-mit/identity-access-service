package lk.ac.kelaniya.ams.identity_access_service.repository;

import lk.ac.kelaniya.ams.identity_access_service.entity.UserRole;
import lk.ac.kelaniya.ams.identity_access_service.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for UserRole join entities.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByIdUserId(UUID userId);

    List<UserRole> findByIdRoleId(UUID roleId);

    void deleteByIdUserId(UUID userId);
}
