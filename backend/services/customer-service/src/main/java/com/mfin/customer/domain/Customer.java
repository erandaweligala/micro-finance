package com.mfin.customer.domain;

import com.mfin.common.crypto.EncryptedStringConverter;
import com.mfin.common.error.ApiExceptions;
import com.mfin.common.persistence.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.UUID;

/**
 * A borrower.
 *
 * <p>Identifying fields (national id, phone, email) are encrypted at rest through
 * {@link EncryptedStringConverter}. Because ciphertext is non-deterministic and therefore
 * unsearchable, each encrypted field is shadowed by a keyed HMAC "blind index" column that
 * supports exact-match lookup - so "is this ID already registered?" stays a single indexed
 * query without storing anything in the clear.</p>
 */
@Entity
@Table(name = "customer",
        uniqueConstraints = {
                @UniqueConstraint(name = "ux_customer_tenant_number",
                        columnNames = {"tenant_id", "customer_number"}),
                @UniqueConstraint(name = "ux_customer_tenant_national_id",
                        columnNames = {"tenant_id", "national_id_index"})
        },
        indexes = {
                @Index(name = "ix_customer_tenant_status", columnList = "tenant_id, status"),
                @Index(name = "ix_customer_tenant_kyc", columnList = "tenant_id, kyc_status"),
                @Index(name = "ix_customer_phone_index", columnList = "phone_index"),
                @Index(name = "ix_customer_name", columnList = "tenant_id, last_name, first_name"),
                @Index(name = "ix_customer_branch", columnList = "tenant_id, branch_id")
        })
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class Customer extends TenantAwareEntity {

    /** Human-facing reference, e.g. {@code CUS-000123}. Unique within the institution. */
    @Column(name = "customer_number", nullable = false, length = 32, updatable = false)
    private String customerNumber;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "middle_name", length = 80)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 24)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 24)
    private MaritalStatus maritalStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "id_type", nullable = false, length = 32)
    private IdentificationType idType;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "national_id", nullable = false, length = 512)
    private String nationalId;

    /** HMAC of the national id; enables uniqueness and lookup without exposing the value. */
    @Column(name = "national_id_index", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String nationalIdIndex;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "phone_number", nullable = false, length = 512)
    private String phoneNumber;

    @Column(name = "phone_index", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String phoneIndex;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "email", length = 512)
    private String email;

    @Column(name = "email_index", length = 64, columnDefinition = "CHAR(64)")
    private String emailIndex;

    @Embedded
    private Address address;

    @Column(name = "occupation", length = 96)
    private String occupation;

    @Column(name = "employer", length = 128)
    private String employer;

    @Column(name = "monthly_income", precision = 19, scale = 4)
    private BigDecimal monthlyIncome;

    @Column(name = "branch_id", columnDefinition = "CHAR(36)")
    private UUID branchId;

    /** Loan officer who owns the relationship. */
    @Column(name = "loan_officer_id", columnDefinition = "CHAR(36)")
    private UUID loanOfficerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 24)
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_verified_at")
    private java.time.Instant kycVerifiedAt;

    @Column(name = "kyc_verified_by", columnDefinition = "CHAR(36)")
    private UUID kycVerifiedBy;

    @Column(name = "kyc_rejection_reason", length = 512)
    private String kycRejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CustomerStatus status = CustomerStatus.ACTIVE;

    @Column(name = "deactivation_reason", length = 512)
    private String deactivationReason;

    protected Customer() {
    }

    public Customer(String customerNumber, String firstName, String lastName,
                    LocalDate dateOfBirth, IdentificationType idType) {
        this.customerNumber = customerNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.idType = idType;
    }

    /** Minimum age to hold a loan contract. */
    public static final int MINIMUM_AGE = 18;

    public void validateAge(LocalDate today) {
        if (dateOfBirth == null || !dateOfBirth.isBefore(today)) {
            throw new ApiExceptions.BusinessRuleException("Date of birth must be in the past");
        }
        if (Period.between(dateOfBirth, today).getYears() < MINIMUM_AGE) {
            throw new ApiExceptions.BusinessRuleException(
                    "A customer must be at least " + MINIMUM_AGE + " years old");
        }
    }

    /** Sets the encrypted identifiers together with their blind indexes, which must stay in step. */
    public void setIdentifiers(String nationalId, String nationalIdIndex,
                               String phoneNumber, String phoneIndex,
                               String email, String emailIndex) {
        this.nationalId = nationalId;
        this.nationalIdIndex = nationalIdIndex;
        this.phoneNumber = phoneNumber;
        this.phoneIndex = phoneIndex;
        this.email = email;
        this.emailIndex = emailIndex;
    }

    public void updatePersonalDetails(String firstName, String middleName, String lastName,
                                      LocalDate dateOfBirth, Gender gender,
                                      MaritalStatus maritalStatus) {
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.maritalStatus = maritalStatus;
    }

    public void updateEmployment(String occupation, String employer, BigDecimal monthlyIncome) {
        this.occupation = occupation;
        this.employer = employer;
        this.monthlyIncome = monthlyIncome;
    }

    public void assign(UUID branchId, UUID loanOfficerId) {
        this.branchId = branchId;
        this.loanOfficerId = loanOfficerId;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    /** Any change to identity documents sends KYC back for re-verification. */
    public void markKycPending() {
        this.kycStatus = KycStatus.PENDING;
        this.kycVerifiedAt = null;
        this.kycVerifiedBy = null;
        this.kycRejectionReason = null;
    }

    public void markKycUnderReview() {
        this.kycStatus = KycStatus.UNDER_REVIEW;
    }

    public void verifyKyc(UUID verifiedBy) {
        if (kycStatus == KycStatus.VERIFIED) {
            throw new ApiExceptions.BusinessRuleException("KYC is already verified");
        }
        this.kycStatus = KycStatus.VERIFIED;
        this.kycVerifiedAt = java.time.Instant.now();
        this.kycVerifiedBy = verifiedBy;
        this.kycRejectionReason = null;
    }

    public void rejectKyc(UUID rejectedBy, String reason) {
        this.kycStatus = KycStatus.REJECTED;
        this.kycVerifiedBy = rejectedBy;
        this.kycVerifiedAt = java.time.Instant.now();
        this.kycRejectionReason = reason;
    }

    /** A loan may only be originated for an active, KYC-verified customer. */
    public boolean isEligibleForLending() {
        return status == CustomerStatus.ACTIVE && kycStatus == KycStatus.VERIFIED;
    }

    public void deactivate(String reason) {
        if (status == CustomerStatus.DEACTIVATED) {
            throw new ApiExceptions.BusinessRuleException("Customer is already deactivated");
        }
        this.status = CustomerStatus.DEACTIVATED;
        this.deactivationReason = reason;
    }

    public void reactivate() {
        this.status = CustomerStatus.ACTIVE;
        this.deactivationReason = null;
    }

    public String getFullName() {
        return middleName == null || middleName.isBlank()
                ? firstName + " " + lastName
                : firstName + " " + middleName + " " + lastName;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public MaritalStatus getMaritalStatus() {
        return maritalStatus;
    }

    public IdentificationType getIdType() {
        return idType;
    }

    public void setIdType(IdentificationType idType) {
        this.idType = idType;
    }

    public String getNationalId() {
        return nationalId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public Address getAddress() {
        return address;
    }

    public String getOccupation() {
        return occupation;
    }

    public String getEmployer() {
        return employer;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public UUID getBranchId() {
        return branchId;
    }

    public UUID getLoanOfficerId() {
        return loanOfficerId;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public java.time.Instant getKycVerifiedAt() {
        return kycVerifiedAt;
    }

    public UUID getKycVerifiedBy() {
        return kycVerifiedBy;
    }

    public String getKycRejectionReason() {
        return kycRejectionReason;
    }

    public CustomerStatus getStatus() {
        return status;
    }

    public String getDeactivationReason() {
        return deactivationReason;
    }
}
