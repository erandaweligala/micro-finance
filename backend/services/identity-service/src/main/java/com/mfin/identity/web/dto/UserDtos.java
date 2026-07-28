package com.mfin.identity.web.dto;

import com.mfin.identity.domain.AppUser;
import com.mfin.identity.domain.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** User administration payloads. */
public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 3, max = 128)
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
                    message = "Username may contain letters, digits, dot, underscore and hyphen only")
            String username,

            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 32) String phoneNumber,

            @Schema(description = "Roles to grant", example = "[\"LOAN_OFFICER\"]")
            @NotEmpty(message = "At least one role is required") Set<String> roles,

            @Schema(description = "Branch the user is posted to") UUID branchId,

            @Schema(description = "Initial password. The user is forced to change it at first sign-in.")
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword
    ) {
    }

    public record UpdateUserRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 160) String fullName,
            @Size(max = 32) String phoneNumber,
            UUID branchId
    ) {
    }

    public record UpdateRolesRequest(
            @NotEmpty(message = "At least one role is required") Set<String> roles
    ) {
    }

    public record AdminResetPasswordRequest(
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {
    }

    @Schema(description = "A user account. Never includes the password hash.")
    public record UserResponse(
            UUID id,
            UUID tenantId,
            String username,
            String email,
            String fullName,
            String phoneNumber,
            List<String> roles,
            UUID branchId,
            UserStatus status,
            boolean locked,
            boolean mustChangePassword,
            Instant lastLoginAt,
            Instant createdAt
    ) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getTenantId(), user.getUsername(),
                    user.getEmail(), user.getFullName(), user.getPhoneNumber(),
                    List.copyOf(user.getRoles()), user.getBranchId(), user.getStatus(),
                    user.isLocked(), user.isMustChangePassword(), user.getLastLoginAt(),
                    user.getCreatedAt());
        }
    }

    /** Provisioning payload used by the tenant service when an institution is onboarded. */
    public record ProvisionTenantAdminRequest(
            UUID tenantId,
            @NotBlank @Size(max = 64) String tenantSlug,
            @NotBlank @Size(max = 160) String tenantName,
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Email String email,
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword
    ) {
    }
}
