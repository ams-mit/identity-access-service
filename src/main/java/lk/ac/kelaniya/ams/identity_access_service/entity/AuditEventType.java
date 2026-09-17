package lk.ac.kelaniya.ams.identity_access_service.entity;

/**
 * Enumeration of security and user lifecycle audit event types.
 */
public enum AuditEventType {
    ACCOUNT_STATUS_CHANGED,
    ROLE_ASSIGNED,
    ROLE_REMOVED,
    USER_REGISTERED,
    USER_CREATED_BY_ADMIN,
    PASSWORD_CHANGED,
    PASSWORD_RESET,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    ACCOUNT_LOCKED
}
