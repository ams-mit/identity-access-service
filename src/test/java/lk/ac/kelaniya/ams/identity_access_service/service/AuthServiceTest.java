package lk.ac.kelaniya.ams.identity_access_service.service;

import lk.ac.kelaniya.ams.identity_access_service.dto.request.RegisterRequest;
import lk.ac.kelaniya.ams.identity_access_service.dto.response.RegisterResponse;
import lk.ac.kelaniya.ams.identity_access_service.entity.AccountStatus;
import lk.ac.kelaniya.ams.identity_access_service.entity.User;
import lk.ac.kelaniya.ams.identity_access_service.exception.DuplicateEmailException;
import lk.ac.kelaniya.ams.identity_access_service.exception.PasswordMismatchException;
import lk.ac.kelaniya.ams.identity_access_service.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = RegisterRequest.builder()
                .firstName("Alice")
                .lastName("Smith")
                .email("alice.smith@example.com")
                .phone("+94712345678")
                .password("StrongPassword1")
                .confirmPassword("StrongPassword1")
                .build();
    }

    @Test
    @DisplayName("register creates user with PENDING_VERIFICATION and hashes password")
    void testRegister_success() {
        UUID expectedId = UUID.randomUUID();
        String hashedPassword = "$2a$10$hashedPasswordSample";

        given(userRepository.existsByEmail("alice.smith@example.com")).willReturn(false);
        given(passwordEncoder.encode("StrongPassword1")).willReturn(hashedPassword);
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(expectedId);
            return user;
        });

        RegisterResponse response = authService.register(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo(expectedId);
        assertThat(response.getEmail()).isEqualTo("alice.smith@example.com");
        assertThat(response.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getPasswordHash()).isEqualTo(hashedPassword);
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("StrongPassword1");
        assertThat(savedUser.getAccountStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(savedUser.getFailedAttemptCount()).isZero();
    }

    @Test
    @DisplayName("register throws PasswordMismatchException when passwords differ")
    void testRegister_passwordMismatch() {
        validRequest.setConfirmPassword("MismatchPassword2");

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(PasswordMismatchException.class)
                .hasMessage("Passwords do not match");

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register throws DuplicateEmailException when email exists")
    void testRegister_duplicateEmail() {
        given(userRepository.existsByEmail("alice.smith@example.com")).willReturn(true);

        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(DuplicateEmailException.class)
                .hasMessage("Email already in use");

        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
