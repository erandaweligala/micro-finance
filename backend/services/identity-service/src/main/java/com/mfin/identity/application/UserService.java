package com.mfin.identity.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.tenant.Roles;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.identity.domain.AppUser;
import com.mfin.identity.domain.TenantDirectoryEntry;
import com.mfin.identity.domain.UserStatus;
import com.mfin.identity.repository.IdentityRepositories.AppUserRepository;
import com.mfin.identity.repository.TenantDirectoryRepository;
import com.mfin.identity.web.dto.UserDtos.AdminResetPasswordRequest;
import com.mfin.identity.web.dto.UserDtos.CreateUserRequest;
import com.mfin.identity.web.dto.UserDtos.ProvisionTenantAdminRequest;
import com.mfin.identity.web.dto.UserDtos.UpdateRolesRequest;
import com.mfin.identity.web.dto.UserDtos.UpdateUserRequest;
import com.mfin.identity.web.dto.UserDtos.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

/**
 * User administration within one institution.
 *
 * <p>Every read and write is keyed on the tenant from the caller's token, so a tenant
 * administrator physically cannot address another institution's users: an id from elsewhere
 * simply does not match and surfaces as 404.</p>
 */
@Service
public class UserService {

    /** Roles a tenant administrator may grant. Notably excludes PLATFORM_ADMIN. */
    private static final Set<String> ASSIGNABLE_ROLES = Set.of(
            Roles.TENANT_ADMIN, Roles.BRANCH_MANAGER, Roles.LOAN_OFFICER,
            Roles.CASHIER, Roles.AUDITOR, Roles.CUSTOMER);

    private final AppUserRepository userRepository;
    private final TenantDirectoryRepository tenantDirectory;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokenService;

    public UserService(AppUserRepository userRepository,
                       TenantDirectoryRepository tenantDirectory,
                       PasswordEncoder passwordEncoder,
                       PasswordPolicy passwordPolicy,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.tenantDirectory = tenantDirectory;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.tokenService = tokenService;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        validateRoles(request.roles());
        if (userRepository.existsByTenantIdAndUsernameIgnoreCase(tenantId, request.username())) {
            throw new ApiExceptions.ConflictException(
                    "A user with this username already exists in your organisation");
        }
        if (userRepository.existsByTenantIdAndEmailIgnoreCase(tenantId, request.email())) {
            throw new ApiExceptions.ConflictException(
                    "A user with this email address already exists in your organisation");
        }
        passwordPolicy.validate(request.temporaryPassword(), request.username(), request.email());

        AppUser user = new AppUser(tenantId, request.username(), request.email(),
                request.fullName(), passwordEncoder.encode(request.temporaryPassword()),
                request.roles());
        user.updateProfile(request.fullName(), request.email(), request.phoneNumber(),
                request.branchId());
        // An administrator-chosen password is a shared secret until the user replaces it.
        user.resetPassword(passwordEncoder.encode(request.temporaryPassword()), true);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String query, UserStatus status, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalised = query == null || query.isBlank() ? null : query.trim();
        return PageResponse.from(
                userRepository.search(tenantId, status, normalised, pageable), UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return UserResponse.from(requireUser(userId));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser() {
        UUID userId = TenantContext.require().userId();
        return UserResponse.from(userRepository.findById(userId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User", userId)));
    }

    @Transactional
    public UserResponse update(UUID userId, UpdateUserRequest request) {
        AppUser user = requireUser(userId);
        user.updateProfile(request.fullName(), request.email(), request.phoneNumber(),
                request.branchId());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateRoles(UUID userId, UpdateRolesRequest request) {
        validateRoles(request.roles());
        AppUser user = requireUser(userId);
        if (user.getId().equals(TenantContext.require().userId())
                && !request.roles().contains(Roles.TENANT_ADMIN)) {
            // Prevents an administrator from locking the whole institution out of its own console.
            throw new ApiExceptions.BusinessRuleException(
                    "You cannot remove your own administrator role");
        }
        user.replaceRoles(request.roles());
        AppUser saved = userRepository.save(user);
        // Roles live in the access token, so existing sessions must be re-issued.
        tokenService.revokeAllForUser(userId);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse deactivate(UUID userId) {
        AppUser user = requireUser(userId);
        if (user.getId().equals(TenantContext.require().userId())) {
            throw new ApiExceptions.BusinessRuleException("You cannot deactivate your own account");
        }
        user.deactivate();
        AppUser saved = userRepository.save(user);
        tokenService.revokeAllForUser(userId);
        return UserResponse.from(saved);
    }

    @Transactional
    public UserResponse activate(UUID userId) {
        AppUser user = requireUser(userId);
        user.activate();
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void adminResetPassword(UUID userId, AdminResetPasswordRequest request) {
        AppUser user = requireUser(userId);
        passwordPolicy.validate(request.newPassword(), user.getUsername(), user.getEmail());
        user.resetPassword(passwordEncoder.encode(request.newPassword()), true);
        userRepository.save(user);
        tokenService.revokeAllForUser(userId);
    }

    /**
     * Creates the first administrator for a newly onboarded institution, together with the local
     * directory entry that makes its slug resolvable at sign-in. Platform operators only.
     */
    @Transactional
    public UserResponse provisionTenantAdmin(ProvisionTenantAdminRequest request) {
        tenantDirectory.findBySlugIgnoreCase(request.tenantSlug()).ifPresentOrElse(
                entry -> entry.update(request.tenantName(), true),
                () -> tenantDirectory.save(new TenantDirectoryEntry(request.tenantId(),
                        request.tenantSlug(), request.tenantName())));

        if (userRepository.existsByTenantIdAndUsernameIgnoreCase(request.tenantId(), request.username())) {
            throw new ApiExceptions.ConflictException("This administrator already exists");
        }
        passwordPolicy.validate(request.temporaryPassword(), request.username(), request.email());

        AppUser admin = new AppUser(request.tenantId(), request.username(), request.email(),
                request.fullName(), passwordEncoder.encode(request.temporaryPassword()),
                Set.of(Roles.TENANT_ADMIN));
        admin.resetPassword(passwordEncoder.encode(request.temporaryPassword()), true);
        return UserResponse.from(userRepository.save(admin));
    }

    private AppUser requireUser(UUID userId) {
        UUID tenantId = TenantContext.requireTenantId();
        return userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("User", userId));
    }

    private void validateRoles(Set<String> roles) {
        for (String role : roles) {
            if (!ASSIGNABLE_ROLES.contains(role)) {
                throw new ApiExceptions.BusinessRuleException(
                        "Role '" + role + "' cannot be granted here");
            }
        }
    }
}
