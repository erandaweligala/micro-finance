package com.mfin.identity.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Request and response payloads for authentication. */
public final class AuthDtos {

    private AuthDtos() {
    }

    @Schema(description = "Sign-in request. The organisation is identified by its slug, which the "
            + "app collects on the tenant-selection screen or derives from the login subdomain.")
    public record LoginRequest(
            @Schema(example = "acme-microfinance")
            @NotBlank(message = "Organisation is required")
            @Size(max = 64)
            String tenantSlug,

            @Schema(example = "jane.officer")
            @NotBlank(message = "Username is required")
            @Size(max = 128)
            String username,

            @NotBlank(message = "Password is required")
            @Size(max = 128)
            String password
    ) {
    }

    @Schema(description = "Platform operator sign-in; no organisation, since these users span tenants.")
    public record PlatformLoginRequest(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required") String refreshToken
    ) {
    }

    @Schema(description = "Issued tokens plus the profile the app needs to render its first screen")
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            UUID userId,
            UUID tenantId,
            String tenantSlug,
            String username,
            String fullName,
            List<String> roles,
            @Schema(description = "When true the app must route to the change-password screen")
            boolean mustChangePassword
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {
    }

    @Schema(description = "Starts password recovery. Always returns 202 so it cannot be used to "
            + "discover which email addresses are registered.")
    public record ForgotPasswordRequest(
            @NotBlank @Size(max = 64) String tenantSlug,
            @NotBlank @Email @Size(max = 255) String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {
    }
}
