package com.mfin.customer.domain;

import com.mfin.common.crypto.EncryptedStringConverter;
import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A KYC document submitted for a customer.
 *
 * <p>Only the storage key is held here - the file itself lives in object storage behind a
 * short-lived signed URL, so scans of identity documents never pass through this database or
 * its backups.</p>
 */
@Entity
@Table(name = "kyc_document", indexes = {
        @Index(name = "ix_kyc_document_customer", columnList = "tenant_id, customer_id"),
        @Index(name = "ix_kyc_document_status", columnList = "tenant_id, verification_status")
})
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class KycDocument extends TenantAwareEntity {

    @Column(name = "customer_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private DocumentType documentType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "document_number", length = 512)
    private String documentNumber;

    @Column(name = "issuing_authority", length = 128)
    private String issuingAuthority;

    @Column(name = "issued_on")
    private LocalDate issuedOn;

    @Column(name = "expires_on")
    private LocalDate expiresOn;

    /** Object-storage key, e.g. {@code tenant/{id}/customer/{id}/passport.jpg}. */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", length = 96)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** SHA-256 of the uploaded file, so tampering after upload is detectable. */
    @Column(name = "checksum", length = 64, columnDefinition = "CHAR(64)")
    private String checksum;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 24)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "verified_by", columnDefinition = "CHAR(36)")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "rejection_reason", length = 512)
    private String rejectionReason;

    public enum VerificationStatus {
        PENDING, VERIFIED, REJECTED
    }

    protected KycDocument() {
    }

    public KycDocument(UUID customerId, DocumentType documentType, String storageKey) {
        this.customerId = customerId;
        this.documentType = documentType;
        this.storageKey = storageKey;
    }

    public void describe(String documentNumber, String issuingAuthority, LocalDate issuedOn,
                         LocalDate expiresOn, String contentType, Long fileSizeBytes,
                         String checksum) {
        this.documentNumber = documentNumber;
        this.issuingAuthority = issuingAuthority;
        this.issuedOn = issuedOn;
        this.expiresOn = expiresOn;
        this.contentType = contentType;
        this.fileSizeBytes = fileSizeBytes;
        this.checksum = checksum;
    }

    public void verify(UUID officerId) {
        this.verificationStatus = VerificationStatus.VERIFIED;
        this.verifiedBy = officerId;
        this.verifiedAt = Instant.now();
        this.rejectionReason = null;
    }

    public void reject(UUID officerId, String reason) {
        this.verificationStatus = VerificationStatus.REJECTED;
        this.verifiedBy = officerId;
        this.verifiedAt = Instant.now();
        this.rejectionReason = reason;
    }

    /** An expired document cannot support a verified KYC status. */
    public boolean isExpired(LocalDate today) {
        return expiresOn != null && expiresOn.isBefore(today);
    }

    public boolean isVerified() {
        return verificationStatus == VerificationStatus.VERIFIED;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getIssuingAuthority() {
        return issuingAuthority;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public LocalDate getExpiresOn() {
        return expiresOn;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public String getChecksum() {
        return checksum;
    }

    public VerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public UUID getVerifiedBy() {
        return verifiedBy;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }
}
