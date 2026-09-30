package lk.ac.kelaniya.ams.identity_access_service.exception;

/**
 * Thrown when an untrusted or unrecognized microservice identifier is supplied for service JWT issuance.
 */
public class UntrustedServiceException extends RuntimeException {

    public UntrustedServiceException(String message) {
        super(message);
    }
}
