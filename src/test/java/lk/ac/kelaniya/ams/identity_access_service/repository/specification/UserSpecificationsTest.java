package lk.ac.kelaniya.ams.identity_access_service.repository.specification;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserSpecificationsTest {

    @Mock
    private Root<User> root;

    @Mock
    private CriteriaQuery<?> query;

    @Mock
    private CriteriaBuilder cb;

    @Test
    @DisplayName("withFilters with null criteria builds empty conjunction predicate")
    void testWithFilters_allNull_buildsPredicate() {
        Predicate andPredicate = mock(Predicate.class);
        given(cb.and(any(Predicate[].class))).willReturn(andPredicate);

        Specification<User> spec = UserSpecifications.withFilters(null, null, null);
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).and(any(Predicate[].class));
    }

    @Test
    @DisplayName("withFilters with all parameters builds search, status, and role predicates")
    void testWithFilters_withAllParams_buildsPredicates() {
        @SuppressWarnings("unchecked")
        Path<Object> firstNamePath = (Path<Object>) mock(Path.class);
        @SuppressWarnings("unchecked")
        Path<Object> lastNamePath = (Path<Object>) mock(Path.class);
        @SuppressWarnings("unchecked")
        Path<Object> emailPath = (Path<Object>) mock(Path.class);
        @SuppressWarnings("unchecked")
        Path<Object> statusPath = (Path<Object>) mock(Path.class);
        @SuppressWarnings("unchecked")
        Path<Object> requestedRolePath = (Path<Object>) mock(Path.class);

        @SuppressWarnings("unchecked")
        Expression<String> lowerExpr = (Expression<String>) mock(Expression.class);
        @SuppressWarnings("unchecked")
        Expression<String> upperExpr = (Expression<String>) mock(Expression.class);
        @SuppressWarnings("unchecked")
        Expression<String> concatExpr = (Expression<String>) mock(Expression.class);

        Predicate orPredicate = mock(Predicate.class);
        Predicate statusPredicate = mock(Predicate.class);
        Predicate rolePredicate = mock(Predicate.class);
        Predicate andPredicate = mock(Predicate.class);

        given(root.get("firstName")).willReturn(firstNamePath);
        given(root.get("lastName")).willReturn(lastNamePath);
        given(root.get("email")).willReturn(emailPath);
        given(root.get("accountStatus")).willReturn(statusPath);
        given(root.get("requestedRole")).willReturn(requestedRolePath);

        given(cb.lower(any())).willReturn(lowerExpr);
        given(cb.like(any(), anyString())).willReturn(mock(Predicate.class));
        given(cb.coalesce(any(), anyString())).willReturn(concatExpr);
        given(cb.concat(any(Expression.class), any(String.class))).willReturn(concatExpr);
        given(cb.concat(any(Expression.class), any(Expression.class))).willReturn(concatExpr);
        given(cb.or(any(Predicate[].class))).willReturn(orPredicate);

        given(cb.equal(statusPath, AccountStatus.PENDING_VERIFICATION)).willReturn(statusPredicate);
        given(cb.upper(any())).willReturn(upperExpr);
        given(cb.equal(upperExpr, "OWNER")).willReturn(rolePredicate);
        given(cb.and(any(Predicate[].class))).willReturn(andPredicate);

        Specification<User> spec = UserSpecifications.withFilters("john", AccountStatus.PENDING_VERIFICATION, "owner");
        Predicate result = spec.toPredicate(root, query, cb);

        assertThat(result).isNotNull();
        verify(cb).equal(statusPath, AccountStatus.PENDING_VERIFICATION);
        verify(cb).equal(upperExpr, "OWNER");
        verify(cb).and(any(Predicate[].class));
    }
}
