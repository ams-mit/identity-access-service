package kln.ams.identityaccess.service;

import kln.ams.identityaccess.dto.auth.AuthenticatedUserResponse;
import kln.ams.identityaccess.dto.auth.LoginRequest;
import kln.ams.identityaccess.dto.auth.LoginResponse;
import kln.ams.identityaccess.dto.auth.LoginUserDto;
import kln.ams.identityaccess.entity.AccountStatus;
import kln.ams.identityaccess.entity.User;
import kln.ams.identityaccess.entity.UserRole;
import kln.ams.identityaccess.exception.AccountInactiveException;
import kln.ams.identityaccess.exception.InvalidCredentialsException;
import kln.ams.identityaccess.exception.UserNotFoundException;
import kln.ams.identityaccess.repository.UserRepository;
import kln.ams.identityaccess.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String identifier = request.getUsername().trim();

        User user = userRepository.findByUsernameIgnoreCase(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElseThrow(() -> {
                    log.warn("Login attempt for non-existent user/email: {}", identifier);
                    return new InvalidCredentialsException();
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("Login failed: invalid password for user '{}'", user.getUsername());
            throw new InvalidCredentialsException();
        }

        if (user.getAccountStatus() != AccountStatus.ACTIVE || user.isAccountLocked()) {
            log.warn("Login rejected: account '{}' has status '{}' or is locked", user.getUsername(), user.getAccountStatus());
            throw new AccountInactiveException("Account is inactive or suspended");
        }

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        String token = jwtService.generateToken(user.getId(), roles);
        log.info("Login successful for user '{}' (id: {})", user.getUsername(), user.getId());

        LoginUserDto userDto = LoginUserDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .roles(roles)
                .status(user.getAccountStatus().name())
                .build();

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(1800)
                .user(userDto)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + userId));

        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountInactiveException("Account is inactive or suspended");
        }

        List<String> roles = user.getUserRoles() != null
                ? user.getUserRoles().stream()
                        .map(ur -> ur.getRole().getName())
                        .sorted()
                        .toList()
                : Collections.emptyList();

        return AuthenticatedUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(roles)
                .status(user.getAccountStatus().name())
                .build();
    }
}
