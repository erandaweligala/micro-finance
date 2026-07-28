package com.mfin.identity.repository;

import com.mfin.identity.domain.TenantDirectoryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TenantDirectoryRepository extends JpaRepository<TenantDirectoryEntry, UUID> {

    Optional<TenantDirectoryEntry> findBySlugIgnoreCase(String slug);

    Optional<TenantDirectoryEntry> findByTenantId(UUID tenantId);
}
