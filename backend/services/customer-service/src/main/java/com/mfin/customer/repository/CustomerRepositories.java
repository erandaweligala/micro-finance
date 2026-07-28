package com.mfin.customer.repository;

import com.mfin.customer.domain.Customer;
import com.mfin.customer.domain.CustomerStatus;
import com.mfin.customer.domain.KycDocument;
import com.mfin.customer.domain.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CustomerRepositories {

    private CustomerRepositories() {
    }

    @Repository
    public interface CustomerRepository extends JpaRepository<Customer, UUID> {

        Optional<Customer> findByIdAndTenantId(UUID id, UUID tenantId);

        Optional<Customer> findByTenantIdAndCustomerNumber(UUID tenantId, String customerNumber);

        /** Exact-match lookup over the encrypted national id, via its blind index. */
        Optional<Customer> findByTenantIdAndNationalIdIndex(UUID tenantId, String nationalIdIndex);

        boolean existsByTenantIdAndNationalIdIndex(UUID tenantId, String nationalIdIndex);

        List<Customer> findByTenantIdAndPhoneIndex(UUID tenantId, String phoneIndex);

        /**
         * Customer search.
         *
         * <p>Free-text matches names and the customer number only. Phone and national id are
         * encrypted, so they are matched through their blind indexes, which the service layer
         * computes from the search term before calling this.</p>
         */
        @Query("""
                select c from Customer c
                where c.tenantId = :tenantId
                  and (:status is null or c.status = :status)
                  and (:kycStatus is null or c.kycStatus = :kycStatus)
                  and (:branchId is null or c.branchId = :branchId)
                  and (:loanOfficerId is null or c.loanOfficerId = :loanOfficerId)
                  and (:search is null
                       or lower(c.firstName) like lower(concat('%', :search, '%'))
                       or lower(c.lastName) like lower(concat('%', :search, '%'))
                       or lower(c.customerNumber) like lower(concat('%', :search, '%'))
                       or c.phoneIndex = :exactIndex
                       or c.nationalIdIndex = :exactIndex)
                """)
        Page<Customer> search(@Param("tenantId") UUID tenantId,
                              @Param("search") String search,
                              @Param("exactIndex") String exactIndex,
                              @Param("status") CustomerStatus status,
                              @Param("kycStatus") KycStatus kycStatus,
                              @Param("branchId") UUID branchId,
                              @Param("loanOfficerId") UUID loanOfficerId,
                              Pageable pageable);

        long countByTenantId(UUID tenantId);

        long countByTenantIdAndStatus(UUID tenantId, CustomerStatus status);

        long countByTenantIdAndKycStatus(UUID tenantId, KycStatus kycStatus);

        /** Sequence source for the human-facing customer number. */
        @Query("select coalesce(max(cast(substring(c.customerNumber, 5) as integer)), 0) "
                + "from Customer c where c.tenantId = :tenantId")
        long maxCustomerSequence(@Param("tenantId") UUID tenantId);
    }

    @Repository
    public interface KycDocumentRepository extends JpaRepository<KycDocument, UUID> {

        List<KycDocument> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);

        Optional<KycDocument> findByIdAndTenantId(UUID id, UUID tenantId);

        long countByTenantIdAndCustomerIdAndVerificationStatus(
                UUID tenantId, UUID customerId, KycDocument.VerificationStatus status);
    }
}
