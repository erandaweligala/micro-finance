package com.mfin.product.repository;

import com.mfin.product.domain.LoanProduct;
import com.mfin.product.domain.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {

    /**
     * Tenant id is always an explicit predicate. The Hibernate tenant filter would usually add
     * it, but {@code findById} bypasses the filter entirely, so relying on it here would be a
     * cross-tenant read waiting to happen.
     */
    Optional<LoanProduct> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<LoanProduct> findByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    boolean existsByTenantIdAndCodeIgnoreCase(UUID tenantId, String code);

    @Query("""
            select p from LoanProduct p
            where p.tenantId = :tenantId
              and (:status is null or p.status = :status)
              and (:search is null
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(p.code) like lower(concat('%', :search, '%')))
            """)
    Page<LoanProduct> search(@Param("tenantId") UUID tenantId,
                             @Param("status") ProductStatus status,
                             @Param("search") String search,
                             Pageable pageable);

    long countByTenantId(UUID tenantId);
}
