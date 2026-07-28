package com.mfin.identity.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.NotificationEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.Roles;
import com.mfin.identity.config.IdentityProperties;
import com.mfin.identity.domain.AppUser;
import com.mfin.identity.domain.PasswordResetToken;
import com.mfin.identity.domain.TenantDirectoryEntry;
import com.mfin.identity.repository.IdentityRepositories.AppUserRepository;
import com.mfin.identity.repository.IdentityRepositories.PasswordResetTokenRepository;
import com.mfin.identity.repository.TenantDirectoryRepository;
import com.mfin.identity.web.dto.AuthDtos.ChangePasswordRequest;
import com.mfin.identity.web.dto.AuthDtos.ForgotPasswordRequest;
import com.mfin.identity.web.dto.AuthDtos.LoginRequest;
import com.mfin.identity.web.dto.AuthDtos.PlatformLoginRequest;
import com.mfin.identity.web.dto.AuthDtos.ResetPasswordRequest;
import com.mfin.identity.web.dto.AuthDtos.TokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Sign-in, password recovery and password change.
 *
 * <p>Two principles run through this class. First, <em>fail identically</em>: a wrong username,
 * a wrong password and an unknown organisation all produce the same message, so the endpoint
 * cannot be used to enumerate users or tenants. Second, <em>always do the work</em>: a
 * password is verified even when the user does not exist, so response timing does not leak
 * existence either.</p>
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * A valid BCrypt hash of a random value, used to burn the same CPU time when no user is
     * found. Without this, "unknown user" would return measurably faster than "wrong password".
     */
    private static final String DUMMY_HASH =
            "$2a$12$C6UzMDM.H6dfI/f/IKcEe.7HWQFQPr0zEHy4z9Q8dyKtGRNlnW.9a";

    private static final String GENERIC_FAILURE = "Invalid organisation, username or password";

    private final AppUserRepository userRepository;
    private final TenantDirectoryRepository tenantDirectory;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokenService;
    private final IdentityProperties properties;
    private final DomainEventPublisher eventPublisher;

    public AuthenticationService(AppUserRepository userRepository,
                                 TenantDirectoryRepository tenantDirectory,
                                 PasswordResetTokenRepository resetTokenRepository,
                                 PasswordEncoder passwordEncoder,
                                 PasswordPolicy passwordPolicy,
                                 TokenService tokenService,
                                 IdentityProperties properties,
                                 DomainEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.tenantDirectory = tenantDirectory;
        this.resetTokenRepository = resetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public TokenResponse login(LoginRequest request, String userAgent, String ipAddress) {
        Optional<TenantDirectoryEntry> tenant = tenantDirectory.findBySlugIgnoreCase(request.tenantSlug());
        Optional<AppUser> found = tenant
                .filter(TenantDirectoryEntry::isLoginEnabled)
                .flatMap(entry -> userRepository
                        .findByTenantIdAndUsernameIgnoreCase(entry.getTenantId(), request.username()));

        AppUser user = authenticate(found, request.password());
        return tokenService.issue(user, tenant.map(TenantDirectoryEntry::getSlug).orElse(null),
                userAgent, ipAddress);
    }

    @Transactional
    public TokenResponse loginPlatformOperator(PlatformLoginRequest request, String userAgent,
                                               String ipAddress) {
        Optional<AppUser> found = userRepository.findPlatformUserByUsername(request.username())
                .filter(candidate -> candidate.getRoles().contains(Roles.PLATFORM_ADMIN));
        AppUser user = authenticate(found, request.password());
        return tokenService.issue(user, null, userAgent, ipAddress);
    }

    /**
     * Shared credential check. Takes an {@link Optional} rather than a user so that the
     * "no such user" path runs the same BCrypt comparison as the real one.
     */
    private AppUser authenticate(Optional<AppUser> found, String rawPassword) {
        String hash = found.map(AppUser::getPasswordHash).orElse(DUMMY_HASH);
        boolean passwordMatches = passwordEncoder.matches(rawPassword, hash);

        AppUser user = found.orElseThrow(() -> new ApiExceptions.AccessDeniedException(GENERIC_FAILURE));

        if (user.isLocked()) {
            // Worth distinguishing: the user has already proven they exist by locking the account,
            // and telling them to wait is far better than an unexplained rejection.
            throw new ApiExceptions.AccessDeniedException(
                    "This account is temporarily locked after repeated failed sign-in attempts. "
                            + "Try again later or reset your password.");
        }
        if (!passwordMatches) {
            user.registerFailedLogin(properties.getMaxFailedLogins(), properties.getLockoutMinutes());
            userRepository.save(user);
            // Log the attempt without the credential.
            log.warn("Failed sign-in for user {} (tenant {})", user.getId(), user.getTenantId());
            throw new ApiExceptions.AccessDeniedException(GENERIC_FAILURE);
        }
        if (!user.canSignIn()) {
            throw new ApiExceptions.AccessDeniedException(
                    "This account is not active. Contact your administrator.");
        }

        user.registerSuccessfulLogin();
        userRepository.save(user);
        return user;
    }

    @Transactional
    public TokenResponse refresh(String refreshToken, String userAgent, String ipAddress) {
        // The user id is taken from the stored token, never from the client.
        AppUser user = tokenService.resolveUserForRefresh(refreshToken);
        String slug = user.getTenantId() == null ? null
                : tenantDirectory.findByTenantId(user.getTenantId())
                        .map(TenantDirectoryEntry::getSlug).orElse(null);
        if (!user.canSignIn()) {
            throw new ApiExceptions.AccessDeniedException("This account is no longer active");
        }
        return tokenService.refresh(refreshToken, user, slug, userAgent, ipAddress);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User", userId));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ApiExceptions.AccessDeniedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ApiExceptions.BusinessRuleException(
                    "The new password must differ from the current one");
        }
        passwordPolicy.validate(request.newPassword(), user.getUsername(), user.getEmail());
        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Changing a password invalidates every other session; that is the point of changing it.
        tokenService.revokeAllForUser(userId);
    }

    /**
     * Starts recovery. Returns the token so the caller can hand it to the notification service;
     * the HTTP layer never returns it to the client.
     */
    @Transactional
    public Optional<PasswordResetIssue> requestPasswordReset(ForgotPasswordRequest request) {
        Optional<AppUser> user = tenantDirectory.findBySlugIgnoreCase(request.tenantSlug())
                .flatMap(entry -> userRepository
                        .findByTenantIdAndEmailIgnoreCase(entry.getTenantId(), request.email()));
        if (user.isEmpty() || !user.get().canSignIn()) {
            // Deliberately silent: the endpoint responds 202 either way.
            log.info("Password reset requested for an unknown or inactive address");
            return Optional.empty();
        }
        AppUser target = user.get();
        String token = randomToken();
        resetTokenRepository.save(new PasswordResetToken(target.getId(), sha256(token),
                Instant.now().plus(properties.getPasswordResetTtl())));
        return Optional.of(new PasswordResetIssue(target.getId(), target.getEmail(),
                target.getFullName(), token));
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken token = resetTokenRepository.findByTokenHash(sha256(request.token()))
                .filter(PasswordResetToken::isRedeemable)
                .orElseThrow(() -> new ApiExceptions.BusinessRuleException(
                        "This password reset link is invalid or has expired"));
        AppUser user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User", token.getUserId()));

        passwordPolicy.validate(request.newPassword(), user.getUsername(), user.getEmail());
        user.resetPassword(passwordEncoder.encode(request.newPassword()), false);
        token.markUsed();
        userRepository.save(user);
        tokenService.revokeAllForUser(user.getId());
    }

    /**
     * Starts recovery and queues the email. The reset token is handed to the notification
     * service through the outbox and is never returned over HTTP.
     */
    @Transactional
    public void startPasswordRecovery(ForgotPasswordRequest request) {
        requestPasswordReset(request).ifPresent(issue -> eventPublisher.publish(
                new NotificationEvents.NotificationRequested(
                        UUID.randomUUID(),
                        null,
                        NotificationEvents.Channel.EMAIL,
                        "PASSWORD_RESET",
                        issue.email(),
                        Map.of("fullName", issue.fullName(),
                                "resetToken", issue.token(),
                                "expiryMinutes", String.valueOf(properties.getPasswordResetTtl().toMinutes())),
                        issue.userId(),
                        Instant.now())));
    }

    /** Revokes the presented refresh token, ending that session. */
    @Transactional
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    /** What the caller needs to send the recovery email. Never serialised to an HTTP response. */
    public record PasswordResetIssue(UUID userId, String email, String fullName, String token) {
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
