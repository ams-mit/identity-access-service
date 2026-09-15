package lk.ac.kelaniya.ams.identity_access_service.repository;

import lk.ac.kelaniya.ams.identity_access_service.entity.PasswordResetToken;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for PasswordResetToken entity management.
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Look up a password reset token by its cryptographic token hash.
     *
     * @param tokenHash cryptographic hash of the reset token
     * @return Optional containing the matching token entity if found
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Invalidate all unexpired/unused reset tokens for a given user by setting used_at to the current timestamp.
     * Ensures only the latest requested token remains valid.
     *
     * @param user target user
     * @param now  invalidation timestamp
     */
    @Modifying
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user = :user AND t.usedAt IS NULL")
    void invalidateAllActiveTokensForUser(@Param("user") User user, @Param("now") Instant now);

    /**
     * Find all password reset tokens associated with a given user.
     *
     * @param user target user
     * @return list of password reset tokens
     */
    List<PasswordResetToken> findAllByUser(User user);
}
