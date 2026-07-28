package com.mfin.origination.repository;

import com.mfin.origination.domain.LoanApplication;
import com.mfin.origination.domain.LoanApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<LoanApplication> findByTenantIdAndApplicationNumber(UUID tenantId, String number);

    @Query("""
            select a from LoanApplication a
            where a.tenantId = :tenantId
              and (:status is null or a.status = :status)
              and (:customerId is null or a.customerId = :customerId)
              and (:branchId is null or a.branchId = :branchId)
              and (:search is null
                   or lower(a.applicationNumber) like lower(concat('%', :search, '%'))
                   or lower(a.customerName) like lower(concat('%', :search, '%')))
            """)
    Page<LoanApplication> search(@Param("tenantId") UUID tenantId,
                                 @Param("status") LoanApplicationStatus status,
                                 @Param("customerId") UUID customerId,
                                 @Param("branchId") UUID branchId,
                                 @Param("search") String search,
                                 Pageable pageable);

    @Query("select coalesce(max(cast(substring(a.applicationNumber, 5) as integer)), 0) "
            + "from LoanApplication a where a.tenantId = :tenantId")
    long maxApplicationSequence(@Param("tenantId") UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, LoanApplicationStatus status);
}
