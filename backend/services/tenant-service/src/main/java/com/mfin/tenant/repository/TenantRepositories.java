package com.mfin.tenant.repository;

import com.mfin.tenant.domain.Branch;
import com.mfin.tenant.domain.SubscriptionPlan;
import com.mfin.tenant.domain.Tenant;
import com.mfin.tenant.domain.TenantSetting;
import com.mfin.tenant.domain.TenantStatus;
import com.mfin.tenant.domain.TenantSubscription;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TenantRepositories {

    private TenantRepositories() {
    }

    public interface TenantRepository extends JpaRepository<Tenant, UUID> {

        Optional<Tenant> findBySlugIgnoreCase(String slug);

        Optional<Tenant> findBySubdomainIgnoreCase(String subdomain);

        boolean existsBySlugIgnoreCase(String slug);

        @Query("""
                select t from Tenant t
                where (:status is null or t.status = :status)
                  and (:search is null
                       or lower(t.name) like lower(concat('%', :search, '%'))
                       or lower(t.slug) like lower(concat('%', :search, '%')))
                """)
        Page<Tenant> search(@Param("status") TenantStatus status,
                            @Param("search") String search,
                            Pageable pageable);
    }

    public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, UUID> {

        Optional<SubscriptionPlan> findByCodeIgnoreCase(String code);

        List<SubscriptionPlan> findByActiveTrueOrderByMonthlyPriceAsc();
    }

    public interface TenantSubscriptionRepository extends JpaRepository<TenantSubscription, UUID> {

        Optional<TenantSubscription> findFirstByTenantIdOrderByStartsOnDesc(UUID tenantId);
    }

    public interface BranchRepository extends JpaRepository<Branch, UUID> {

        Optional<Branch> findByIdAndTenantId(UUID id, UUID tenantId);

        List<Branch> findByTenantIdOrderByNameAsc(UUID tenantId);

        boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

        long countByTenantIdAndActiveTrue(UUID tenantId);
    }

    public interface TenantSettingRepository extends JpaRepository<TenantSetting, UUID> {

        List<TenantSetting> findByTenantId(UUID tenantId);

        Optional<TenantSetting> findByTenantIdAndKey(UUID tenantId, String key);
    }
}
