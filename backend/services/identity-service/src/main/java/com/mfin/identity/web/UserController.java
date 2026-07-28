package com.mfin.identity.web;

import com.mfin.common.tenant.Roles;
import com.mfin.common.web.PageResponse;
import com.mfin.identity.application.UserService;
import com.mfin.identity.domain.UserStatus;
import com.mfin.identity.web.dto.UserDtos.AdminResetPasswordRequest;
import com.mfin.identity.web.dto.UserDtos.CreateUserRequest;
import com.mfin.identity.web.dto.UserDtos.ProvisionTenantAdminRequest;
import com.mfin.identity.web.dto.UserDtos.UpdateRolesRequest;
import com.mfin.identity.web.dto.UserDtos.UpdateUserRequest;
import com.mfin.identity.web.dto.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "User administration within an institution")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in user's own profile")
    public UserResponse me() {
        return userService.currentUser();
    }

    @PostMapping
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Create a user")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "Search users in your institution")
    public PageResponse<UserResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UserStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "fullName",
                    direction = Sort.Direction.ASC) Pageable pageable) {
        return userService.search(query, status, pageable);
    }

    @GetMapping("/{userId}")
    @PreAuthorize(Roles.Has.MANAGEMENT)
    @Operation(summary = "Fetch one user")
    public UserResponse get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @PutMapping("/{userId}")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Update a user's profile")
    public UserResponse update(@PathVariable UUID userId,
                               @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(userId, request);
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Replace a user's roles",
            description = "Existing sessions are revoked, since roles are carried in the token.")
    public UserResponse updateRoles(@PathVariable UUID userId,
                                    @Valid @RequestBody UpdateRolesRequest request) {
        return userService.updateRoles(userId, request);
    }

    @PostMapping("/{userId}/password-reset")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Set a temporary password for a user")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@PathVariable UUID userId,
                              @Valid @RequestBody AdminResetPasswordRequest request) {
        userService.adminResetPassword(userId, request);
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Reactivate a user")
    public UserResponse activate(@PathVariable UUID userId) {
        return userService.activate(userId);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize(Roles.Has.TENANT_ADMIN)
    @Operation(summary = "Deactivate a user",
            description = "The account is disabled, not deleted, so audit history stays intact.")
    public UserResponse deactivate(@PathVariable UUID userId) {
        return userService.deactivate(userId);
    }

    @PostMapping("/provision-tenant-admin")
    @PreAuthorize(Roles.Has.PLATFORM_ADMIN)
    @Operation(summary = "Create the first administrator for a new institution",
            description = "Called during tenant onboarding. Platform operators only.")
    public ResponseEntity<UserResponse> provisionTenantAdmin(
            @Valid @RequestBody ProvisionTenantAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(userService.provisionTenantAdmin(request));
    }
}
