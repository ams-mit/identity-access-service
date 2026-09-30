package kln.ams.identityaccess.repository;

import kln.ams.identityaccess.entity.Role;
import kln.ams.identityaccess.entity.UserRole;
import kln.ams.identityaccess.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    boolean existsByRole(Role role);

    boolean existsByIdRoleId(UUID roleId);
}
