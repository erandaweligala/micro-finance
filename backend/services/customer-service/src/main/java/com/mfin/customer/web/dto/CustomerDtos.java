package com.mfin.customer.web.dto;

import com.mfin.common.crypto.FieldEncryptor;
import com.mfin.customer.domain.Address;
import com.mfin.customer.domain.Customer;
import com.mfin.customer.domain.CustomerStatus;
import com.mfin.customer.domain.DocumentType;
import com.mfin.customer.domain.Gender;
import com.mfin.customer.domain.IdentificationType;
import com.mfin.customer.domain.KycDocument;
import com.mfin.customer.domain.KycStatus;
import com.mfin.customer.domain.MaritalStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Customer and KYC payloads. */
public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record AddressRequest(
            @Size(max = 160) String line1,
            @Size(max = 160) String line2,
            @Size(max = 96) String city,
            @Size(max = 96) String stateProvince,
            @Size(max = 24) String postalCode,
            @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter ISO code")
            String countryCode
    ) {
        public Address toDomain() {
            return new Address(line1, line2, city, stateProvince, postalCode, countryCode);
        }
    }

    public record AddressResponse(String line1, String line2, String city, String stateProvince,
                                  String postalCode, String countryCode) {
        public static AddressResponse from(Address address) {
            return address == null ? null : new AddressResponse(address.getLine1(), address.getLine2(),
                    address.getCity(), address.getStateProvince(), address.getPostalCode(),
                    address.getCountryCode());
        }
    }

    @Schema(description = "Register a new customer")
    public record CreateCustomerRequest(
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String middleName,
            @NotBlank @Size(max = 80) String lastName,

            @NotNull(message = "Date of birth is required")
            @Past(message = "Date of birth must be in the past")
            LocalDate dateOfBirth,

            Gender gender,
            MaritalStatus maritalStatus,

            @NotNull(message = "Identification type is required") IdentificationType idType,

            @NotBlank(message = "Identification number is required")
            @Size(max = 64)
            @Schema(description = "Encrypted at rest and never returned in full")
            String nationalId,

            @NotBlank(message = "Phone number is required")
            @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$", message = "Phone number is not valid")
            String phoneNumber,

            @Email(message = "Email address is not valid") @Size(max = 255) String email,

            @Valid AddressRequest address,

            @Size(max = 96) String occupation,
            @Size(max = 128) String employer,
            @PositiveOrZero BigDecimal monthlyIncome,

            UUID branchId,
            UUID loanOfficerId
    ) {
    }

    @Schema(description = "Update an existing customer. Changing identity documents resets KYC.")
    public record UpdateCustomerRequest(
            @NotBlank @Size(max = 80) String firstName,
            @Size(max = 80) String middleName,
            @NotBlank @Size(max = 80) String lastName,
            @NotNull @Past LocalDate dateOfBirth,
            Gender gender,
            MaritalStatus maritalStatus,
            @Pattern(regexp = "^\\+?[0-9 ()-]{7,20}$") String phoneNumber,
            @Email @Size(max = 255) String email,
            @Valid AddressRequest address,
            @Size(max = 96) String occupation,
            @Size(max = 128) String employer,
            @PositiveOrZero BigDecimal monthlyIncome,
            UUID branchId,
            UUID loanOfficerId
    ) {
    }

    public record DeactivateCustomerRequest(
            @NotBlank(message = "A reason is required") @Size(max = 512) String reason
    ) {
    }

    @Schema(description = """
            A customer. Identifying numbers are masked to their last four characters; the full
            values are decrypted only for the KYC review screen, which is separately authorised.
            """)
    public record CustomerResponse(
            UUID id,
            String customerNumber,
            String firstName,
            String middleName,
            String lastName,
            String fullName,
            LocalDate dateOfBirth,
            Gender gender,
            MaritalStatus maritalStatus,
            IdentificationType idType,
            @Schema(example = "****4821") String nationalIdMasked,
            @Schema(example = "****7788") String phoneNumberMasked,
            String email,
            AddressResponse address,
            String occupation,
            String employer,
            BigDecimal monthlyIncome,
            UUID branchId,
            UUID loanOfficerId,
            KycStatus kycStatus,
            Instant kycVerifiedAt,
            String kycRejectionReason,
            CustomerStatus status,
            boolean eligibleForLending,
            Instant createdAt
    ) {
        public static CustomerResponse from(Customer customer) {
            return new CustomerResponse(
                    customer.getId(), customer.getCustomerNumber(), customer.getFirstName(),
                    customer.getMiddleName(), customer.getLastName(), customer.getFullName(),
                    customer.getDateOfBirth(), customer.getGender(), customer.getMaritalStatus(),
                    customer.getIdType(),
                    FieldEncryptor.mask(customer.getNationalId()),
                    FieldEncryptor.mask(customer.getPhoneNumber()),
                    customer.getEmail(),
                    AddressResponse.from(customer.getAddress()),
                    customer.getOccupation(), customer.getEmployer(), customer.getMonthlyIncome(),
                    customer.getBranchId(), customer.getLoanOfficerId(),
                    customer.getKycStatus(), customer.getKycVerifiedAt(),
                    customer.getKycRejectionReason(), customer.getStatus(),
                    customer.isEligibleForLending(), customer.getCreatedAt());
        }
    }

    @Schema(description = "Compact customer record for list screens")
    public record CustomerSummary(
            UUID id,
            String customerNumber,
            String fullName,
            String phoneNumberMasked,
            KycStatus kycStatus,
            CustomerStatus status,
            UUID branchId
    ) {
        public static CustomerSummary from(Customer customer) {
            return new CustomerSummary(customer.getId(), customer.getCustomerNumber(),
                    customer.getFullName(), FieldEncryptor.mask(customer.getPhoneNumber()),
                    customer.getKycStatus(), customer.getStatus(), customer.getBranchId());
        }
    }

    // ---------------------------------------------------------------- KYC

    public record UploadDocumentRequest(
            @NotNull DocumentType documentType,
            @Size(max = 64) String documentNumber,
            @Size(max = 128) String issuingAuthority,
            @PastOrPresent LocalDate issuedOn,
            LocalDate expiresOn,
            @NotBlank @Size(max = 512)
            @Schema(description = "Object-storage key returned by the upload endpoint")
            String storageKey,
            @Size(max = 96) String contentType,
            Long fileSizeBytes,
            @Size(max = 64) String checksum
    ) {
    }

    public record VerifyKycRequest(
            @NotNull Boolean approved,
            @Size(max = 512)
            @Schema(description = "Required when rejecting")
            String reason
    ) {
    }

    public record DocumentResponse(
            UUID id,
            DocumentType documentType,
            String documentNumberMasked,
            String issuingAuthority,
            LocalDate issuedOn,
            LocalDate expiresOn,
            String storageKey,
            String contentType,
            Long fileSizeBytes,
            KycDocument.VerificationStatus verificationStatus,
            Instant verifiedAt,
            String rejectionReason,
            boolean expired
    ) {
        public static DocumentResponse from(KycDocument document) {
            return new DocumentResponse(document.getId(), document.getDocumentType(),
                    FieldEncryptor.mask(document.getDocumentNumber()),
                    document.getIssuingAuthority(), document.getIssuedOn(), document.getExpiresOn(),
                    document.getStorageKey(), document.getContentType(), document.getFileSizeBytes(),
                    document.getVerificationStatus(), document.getVerifiedAt(),
                    document.getRejectionReason(), document.isExpired(LocalDate.now()));
        }
    }

    @Schema(description = "Internal projection used by loan origination to check eligibility")
    public record CustomerEligibility(
            UUID customerId,
            String fullName,
            boolean eligibleForLending,
            KycStatus kycStatus,
            CustomerStatus status,
            UUID branchId
    ) {
        public static CustomerEligibility from(Customer customer) {
            return new CustomerEligibility(customer.getId(), customer.getFullName(),
                    customer.isEligibleForLending(), customer.getKycStatus(),
                    customer.getStatus(), customer.getBranchId());
        }
    }
}
