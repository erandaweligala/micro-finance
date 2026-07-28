package com.mfin.customer.application;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.events.CustomerEvents;
import com.mfin.common.outbox.DomainEventPublisher;
import com.mfin.common.tenant.TenantContext;
import com.mfin.customer.domain.Customer;
import com.mfin.customer.domain.DocumentType;
import com.mfin.customer.domain.KycDocument;
import com.mfin.customer.repository.CustomerRepositories.CustomerRepository;
import com.mfin.customer.repository.CustomerRepositories.KycDocumentRepository;
import com.mfin.customer.web.dto.CustomerDtos.DocumentResponse;
import com.mfin.customer.web.dto.CustomerDtos.UploadDocumentRequest;
import com.mfin.customer.web.dto.CustomerDtos.VerifyKycRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * KYC document handling and verification.
 *
 * <p>Verification is a two-person-friendly control: whoever uploads documents is not required
 * to be the person who approves them, and the decision, its author and its reason are all
 * recorded and emitted as an event for the audit trail.</p>
 */
@Service
public class KycService {

    /** A customer cannot be verified without at least an identity document and proof of address. */
    private static final List<DocumentType> REQUIRED_DOCUMENTS =
            List.of(DocumentType.IDENTITY_DOCUMENT, DocumentType.PROOF_OF_ADDRESS);

    private final CustomerRepository customerRepository;
    private final KycDocumentRepository documentRepository;
    private final DomainEventPublisher eventPublisher;

    public KycService(CustomerRepository customerRepository,
                      KycDocumentRepository documentRepository,
                      DomainEventPublisher eventPublisher) {
        this.customerRepository = customerRepository;
        this.documentRepository = documentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public DocumentResponse uploadDocument(UUID customerId, UploadDocumentRequest request) {
        Customer customer = requireCustomer(customerId);
        if (request.expiresOn() != null && request.issuedOn() != null
                && !request.expiresOn().isAfter(request.issuedOn())) {
            throw new ApiExceptions.BusinessRuleException(
                    "The expiry date must fall after the issue date");
        }
        if (request.expiresOn() != null && request.expiresOn().isBefore(LocalDate.now())) {
            throw new ApiExceptions.BusinessRuleException("This document has already expired");
        }

        KycDocument document = new KycDocument(customer.getId(), request.documentType(),
                request.storageKey());
        document.describe(request.documentNumber(), request.issuingAuthority(), request.issuedOn(),
                request.expiresOn(), request.contentType(), request.fileSizeBytes(),
                request.checksum());

        // New evidence puts the case back in the queue rather than leaving a stale approval.
        customer.markKycUnderReview();
        customerRepository.save(customer);
        return DocumentResponse.from(documentRepository.save(document));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listDocuments(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        requireCustomer(customerId);
        return documentRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId)
                .stream().map(DocumentResponse::from).toList();
    }

    @Transactional
    public DocumentResponse verifyDocument(UUID customerId, UUID documentId,
                                           VerifyKycRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID officerId = TenantContext.require().userId();
        requireCustomer(customerId);
        KycDocument document = documentRepository.findByIdAndTenantId(documentId, tenantId)
                .filter(candidate -> candidate.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Document", documentId));

        if (Boolean.TRUE.equals(request.approved())) {
            if (document.isExpired(LocalDate.now())) {
                throw new ApiExceptions.BusinessRuleException(
                        "An expired document cannot be approved");
            }
            document.verify(officerId);
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ApiExceptions.BusinessRuleException(
                        "A reason is required when rejecting a document");
            }
            document.reject(officerId, request.reason());
        }
        return DocumentResponse.from(documentRepository.save(document));
    }

    /**
     * Records the overall KYC decision for a customer. Approval requires that every mandatory
     * document type is present, verified and unexpired.
     */
    @Transactional
    public void decideKyc(UUID customerId, VerifyKycRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        UUID officerId = TenantContext.require().userId();
        Customer customer = requireCustomer(customerId);

        if (Boolean.TRUE.equals(request.approved())) {
            List<KycDocument> documents =
                    documentRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(tenantId, customerId);
            List<String> missing = REQUIRED_DOCUMENTS.stream()
                    .filter(required -> documents.stream().noneMatch(document ->
                            document.getDocumentType() == required
                                    && document.isVerified()
                                    && !document.isExpired(LocalDate.now())))
                    .map(Enum::name)
                    .toList();
            if (!missing.isEmpty()) {
                throw new ApiExceptions.BusinessRuleException(
                        "KYC cannot be approved until these documents are verified and unexpired: "
                                + String.join(", ", missing));
            }
            customer.verifyKyc(officerId);
        } else {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new ApiExceptions.BusinessRuleException(
                        "A reason is required when rejecting KYC");
            }
            customer.rejectKyc(officerId, request.reason());
        }

        customerRepository.save(customer);
        eventPublisher.publish(new CustomerEvents.KycStatusChanged(UUID.randomUUID(), tenantId,
                customerId, customer.getKycStatus().name(), officerId, request.reason(),
                Instant.now()));
    }

    private Customer requireCustomer(UUID customerId) {
        UUID tenantId = TenantContext.requireTenantId();
        return customerRepository.findByIdAndTenantId(customerId, tenantId)
                .orElseThrow(() -> new ApiExceptions.ResourceNotFoundException("Customer", customerId));
    }
}
