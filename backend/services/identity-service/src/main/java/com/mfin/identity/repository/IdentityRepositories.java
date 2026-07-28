package com.mfin.identity.repository;

import com.mfin.identity.domain.AppUser;
import com.mfin.identity.domain.PasswordResetToken;
import com.mfin.identity.domain.RefreshToken;
import com.mfin.identity.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repositories for the identity service, grouped so the wiring is visible in one place. */
public final class IdentityRepositories {

    private IdentityRepositories() {
    }

    public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

        /**
         * Looks a user up for sign-in. The tenant is part of the key because two institutions
         * may each have a user called "admin" and they must remain different accounts.
         */
        Optional<AppUser> findByTenantIdAndUsernameIgnoreCase(UUID tenantId, String username);

        /** Platform administrators have no tenant, so they are matched on username alone. */
        @Query("select u from AppUser u where u.tenantId is null and lower(u.username) = lower(:username)")
        Optional<AppUser> findPlatformUserByUsername(@Param("username") String username);

        Optional<AppUser> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

        Optional<AppUser> findByIdAndTenantId(UUID id, UUID tenantId);

        boolean existsByTenantIdAndUsernameIgnoreCase(UUID tenantId, String username);

        boolean existsByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);

        long countByTenantIdAndStatus(UUID tenantId, UserStatus status);

        @Query("""
                select u from AppUser u
                where u.tenantId = :tenantId
                  and (:status is null or u.status = :status)
                  and (:search is null
                       or lower(u.username) like lower(concat('%', :search, '%'))
                       or lower(u.fullName) like lower(concat('%', :search, '%'))
                       or lower(u.email)    like lower(concat('%', :search, '%')))
                """)
        Page<AppUser> search(@Param("tenantId") UUID tenantId,
                             @Param("status") UserStatus status,
                             @Param("search") String search,
                             Pageable pageable);
    }

    public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

        Optional<RefreshToken> findByTokenHash(String tokenHash);

        List<RefreshToken> findByFamilyId(UUID familyId);

        @Modifying
        @Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
        int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

        @Modifying
        @Query("update RefreshToken t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
        int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

        @Modifying
        @Query("delete from RefreshToken t where t.expiresAt < :before")
        int deleteExpired(@Param("before") Instant before);
    }

    public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

        Optional<PasswordResetToken> findByTokenHash(String tokenHash);

        @Modifying
        @Query("delete from PasswordResetToken t where t.expiresAt < :before")
        int deleteExpired(@Param("before") Instant before);
    }
}
