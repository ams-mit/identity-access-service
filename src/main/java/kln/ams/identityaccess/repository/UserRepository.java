package kln.ams.identityaccess.repository;

import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Query("SELECT DISTINCT u FROM User u " +
           "LEFT JOIN u.userRoles ur " +
           "LEFT JOIN ur.role r " +
           "WHERE (:status IS NULL OR u.accountStatus = :status) " +
           "AND (:roleName IS NULL OR UPPER(r.name) = UPPER(:roleName)) " +
           "AND (:search IS NULL OR " +
           "     LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "     LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findUsersFiltered(@Param("status") AccountStatus status,
                                @Param("roleName") String roleName,
                                @Param("search") String search,
                                Pageable pageable);
}
