package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user registration and authentication lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Registers a new user account with PENDING_VERIFICATION status.
     * Enforces password match, uniqueness of email, and BCrypt hashing.
     *
     * @param request the registration request payload
     * @return the created user details
     * @throws PasswordMismatchException if password and confirmPassword do not match
     * @throws DuplicateEmailException   if email is already in use
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match");
        }

        String email = request.getEmail().trim();
        if (userRepository.existsByEmail(email)) {
            log.warn("Registration rejected: duplicate email address attempt");
            throw new DuplicateEmailException("Email already in use");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .email(email)
                .username(email)
                .passwordHash(passwordHash)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(request.getPhone() != null ? request.getPhone().trim() : null)
                .accountStatus(AccountStatus.PENDING_VERIFICATION)
                .failedAttemptCount(0)
                .build();

        User savedUser = userRepository.save(user);

        log.info("User registered successfully with id: {}", savedUser.getId());

        return RegisterResponse.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .accountStatus(savedUser.getAccountStatus())
                .build();
    }
}
