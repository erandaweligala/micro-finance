package com.mfin.identity.web;

import com.mfin.common.tenant.TenantContext;
import com.mfin.identity.application.AuthenticationService;
import com.mfin.identity.web.dto.AuthDtos.ChangePasswordRequest;
import com.mfin.identity.web.dto.AuthDtos.ForgotPasswordRequest;
import com.mfin.identity.web.dto.AuthDtos.LoginRequest;
import com.mfin.identity.web.dto.AuthDtos.PlatformLoginRequest;
import com.mfin.identity.web.dto.AuthDtos.RefreshRequest;
import com.mfin.identity.web.dto.AuthDtos.ResetPasswordRequest;
import com.mfin.identity.web.dto.AuthDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Sign-in, token refresh and password recovery")
public class AuthController {

    private final AuthenticationService authenticationService;

    public AuthController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/login")
    @SecurityRequirements   // public endpoint: no bearer token expected
    @Operation(summary = "Sign in to an institution",
            description = "Returns an access token carrying the tenant and role claims that every "
                    + "other service authorises against.")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        return authenticationService.login(request, httpRequest.getHeader("User-Agent"),
                clientIp(httpRequest));
    }

    @PostMapping("/platform-login")
    @SecurityRequirements
    @Operation(summary = "Sign in as a platform operator",
            description = "For staff who administer the SaaS platform across all institutions.")
    public TokenResponse platformLogin(@Valid @RequestBody PlatformLoginRequest request,
                                       HttpServletRequest httpRequest) {
        return authenticationService.loginPlatformOperator(request,
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for a new access token",
            description = "Refresh tokens rotate on every use; replaying a used token revokes the "
                    + "whole session family.")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 HttpServletRequest httpRequest) {
        return authenticationService.refresh(request.refreshToken(),
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke the presented refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password",
            description = "Signs out every other session on success.")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authenticationService.changePassword(TenantContext.require().userId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @SecurityRequirements
    @Operation(summary = "Request a password reset link",
            description = "Always returns 202, whether or not the address is registered, so the "
                    + "endpoint cannot be used to discover accounts.")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authenticationService.startPasswordRecovery(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/reset-password")
    @SecurityRequirements
    @Operation(summary = "Complete a password reset")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authenticationService.resetPassword(request);
    }

    /**
     * Best-effort client address for the session record. {@code X-Forwarded-For} is only
     * meaningful because the gateway is the sole ingress and overwrites it.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String first = forwarded.split(",")[0].trim();
            return first.length() > 45 ? first.substring(0, 45) : first;
        }
        return request.getRemoteAddr();
    }
}
