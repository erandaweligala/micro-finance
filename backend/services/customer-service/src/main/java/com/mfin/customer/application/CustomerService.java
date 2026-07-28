package com.mfin.customer.application;

import com.mfin.common.crypto.FieldEncryptor;
import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.CustomerEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.common.web.PageResponse;
import com.mfin.customer.domain.Customer;
import com.mfin.customer.domain.CustomerStatus;
import com.mfin.customer.domain.KycStatus;
import com.mfin.customer.repository.CustomerRepositories.CustomerRepository;
import com.mfin.customer.web.dto.CustomerDtos.CreateCustomerRequest;
import com.mfin.customer.web.dto.CustomerDtos.CustomerEligibility;
import com.mfin.customer.web.dto.CustomerDtos.CustomerResponse;
import com.mfin.customer.web.dto.CustomerDtos.CustomerSummary;
import com.mfin.customer.web.dto.CustomerDtos.UpdateCustomerRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Customer registration and maintenance.
 *
 * <p>Encryption is applied here rather than in the entity so that the blind index and the
 * ciphertext are always written together - they must agree, or a customer becomes unfindable.</p>
 */
@Service
public class CustomerService {

    private final CustomerRepository repository;
    private final FieldEncryptor encryptor;
    private final DomainEventPublisher eventPublisher;

    public CustomerService(CustomerRepository repository, FieldEncryptor encryptor,
                           DomainEventPublisher eventPublisher) {
        this.repository = repository;
        this.encryptor = encryptor;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CustomerResponse register(CreateCustomerRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String nationalIdIndex = encryptor.blindIndex(request.nationalId());

        // One identity document, one customer: duplicate borrowers break exposure limits.
        if (repository.existsByTenantIdAndNationalIdIndex(tenantId, nationalIdIndex)) {
            throw new ApiExceptions.ConflictException(
                    "A customer with this identification number is already registered");
        }

        Customer customer = new Customer(nextCustomerNumber(tenantId), request.firstName(),
                request.lastName(), request.dateOfBirth(), request.idType());
        customer.validateAge(LocalDate.now());
        customer.updatePersonalDetails(request.firstName(), request.middleName(), request.lastName(),
                request.dateOfBirth(), request.gender(), request.maritalStatus());
        customer.setIdentifiers(
                request.nationalId(), nationalIdIndex,
                request.phoneNumber(), encryptor.blindIndex(request.phoneNumber()),
                request.email(), encryptor.blindIndex(request.email()));
        if (request.address() != null) {
            customer.setAddress(request.address().toDomain());
        }
        customer.updateEmployment(request.occupation(), request.employer(), request.monthlyIncome());
        customer.assign(request.branchId(), request.loanOfficerId());

        Customer saved = repository.save(customer);
        eventPublisher.publish(new CustomerEvents.CustomerRegistered(UUID.randomUUID(), tenantId,
                saved.getId(), saved.getCustomerNumber(), saved.getFullName(), saved.getBranchId(),
                Instant.now()));
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse update(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = require(customerId);
        customer.updatePersonalDetails(request.firstName(), request.middleName(), request.lastName(),
                request.dateOfBirth(), request.gender(), request.maritalStatus());
        customer.validateAge(LocalDate.now());

        boolean contactChanged = false;
        if (request.phoneNumber() != null || request.email() != null) {
            String phone = request.phoneNumber() != null ? request.phoneNumber() : customer.getPhoneNumber();
            String email = request.email() != null ? request.email() : customer.getEmail();
            contactChanged = true;
            customer.setIdentifiers(
                    customer.getNationalId(), encryptor.blindIndex(customer.getNationalId()),
                    phone, encryptor.blindIndex(phone),
                    email, encryptor.blindIndex(email));
        }
        if (request.address() != null) {
            customer.setAddress(request.address().toDomain());
        }
        customer.updateEmployment(request.occupation(), request.employer(), request.monthlyIncome());
        customer.assign(request.branchId(), request.loanOfficerId());

        Customer saved = repository.save(customer);
        if (contactChanged) {
            // Downstream services cache contact details for statements and reminders.
            eventPublisher.publish(new CustomerEvents.CustomerUpdated(UUID.randomUUID(),
                    saved.getTenantId(), saved.getId(), saved.getFullName(), Instant.now()));
        }
        return CustomerResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerSummary> search(String query, CustomerStatus status,
                                                KycStatus kycStatus, UUID branchId,
                                                UUID loanOfficerId, Pageable pageable) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalised = query == null || query.isBlank() ? null : query.trim();
        // An encrypted field can only be matched exactly, so the search term is also hashed and
        // compared against the blind indexes.
        String exactIndex = normalised == null ? null : encryptor.blindIndex(normalised);
        return PageResponse.from(repository.search(tenantId, normalised, exactIndex, status,
                kycStatus, branchId, loanOfficerId, pageable), CustomerSummary::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID customerId) {
        return CustomerResponse.from(require(customerId));
    }

    /** Used by loan origination before accepting an application. */
    @Transactional(readOnly = true)
    public CustomerEligibility eligibility(UUID customerId) {
        return CustomerEligibility.from(require(customerId));
    }

    @Transactional
    public CustomerResponse deactivate(UUID customerId, String reason) {
        Customer customer = require(customerId);
        customer.deactivate(reason);
        Customer saved = repository.save(customer);
        eventPublisher.publish(new CustomerEvents.CustomerDeactivated(UUID.randomUUID(),
                saved.getTenantId(), saved.getId(), reason, Instant.now()));
        return CustomerResponse.from(saved);
    }

    @Transactional
    public CustomerResponse reactivate(UUID customerId) {
        Customer customer = require(customerId);
        customer.reactivate();
        return CustomerResponse.from(repository.save(customer));
    }

    private Customer require(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        return repository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Customer", customerId));
    }

    /**
     * Allocates the next human-facing customer number.
     *
     * <p>Derived from the current maximum inside the transaction; the unique constraint on
     * {@code (tenant_id, customer_number)} is what actually prevents a duplicate if two
     * registrations race, and the caller retries on that conflict.</p>
     */
    private String nextCustomerNumber(UUID tenantId) {
        long next = repository.maxCustomerSequence(tenantId) + 1;
        return String.format("CUS-%06d", next);
    }
}
