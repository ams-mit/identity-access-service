package lk.ac.kelaniya.ams.identity_access_service.repository.specification;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specifications for dynamic querying and filtering of User entities.
 */
public final class UserSpecifications {

    private UserSpecifications() {
    }

    /**
     * Constructs a composite specification for filtering users by search query, account status,
     * and requested role.
     *
     * @param query         free-text search term matching first name, last name, full name, or email
     * @param status        account lifecycle status filter
     * @param requestedRole advisory requested role filter
     * @return JPA Specification combining all non-null search criteria
     */
    public static Specification<User> withFilters(String query, AccountStatus status, String requestedRole) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.trim().isEmpty()) {
                String pattern = "%" + query.trim().toLowerCase() + "%";
                Predicate firstNameMatch = cb.like(cb.lower(root.get("firstName")), pattern);
                Predicate lastNameMatch = cb.like(cb.lower(root.get("lastName")), pattern);
                Predicate emailMatch = cb.like(cb.lower(root.get("email")), pattern);

                Expression<String> fullName = cb.concat(
                        cb.concat(cb.coalesce(root.get("firstName"), ""), " "),
                        cb.coalesce(root.get("lastName"), "")
                );
                Predicate fullNameMatch = cb.like(cb.lower(fullName), pattern);

                predicates.add(cb.or(firstNameMatch, lastNameMatch, emailMatch, fullNameMatch));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("accountStatus"), status));
            }

            if (requestedRole != null && !requestedRole.trim().isEmpty()) {
                predicates.add(cb.equal(cb.upper(root.get("requestedRole")), requestedRole.trim().toUpperCase()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
