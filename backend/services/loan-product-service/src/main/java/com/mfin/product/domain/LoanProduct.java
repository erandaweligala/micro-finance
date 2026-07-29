package com.mfin.product.domain;

import com.mfin.common.error.ApiExceptions;
import com.mfin.common.persistence.TenantAwareEntity;
import com.mfin.loan.engine.DayCountConvention;
import com.mfin.loan.engine.FeeCollection;
import com.mfin.loan.engine.FeeType;
import com.mfin.loan.engine.GraceType;
import com.mfin.loan.engine.InterestMethod;
import com.mfin.loan.engine.RepaymentFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A configurable lending product: the envelope of terms inside which individual loans are
 * written.
 *
 * <p>The product carries minimum/maximum bounds rather than fixed values so a loan officer can
 * negotiate within policy, and {@link #validateTerms} is the single place those bounds are
 * enforced - origination, the calculator and the mobile app all go through it, so a term that
 * is illegal in one path is illegal in all of them.</p>
 */
@Entity
@Table(name = "loan_product",
        uniqueConstraints = @UniqueConstraint(name = "ux_loan_product_tenant_code",
                columnNames = {"tenant_id", "code"}),
        indexes = @Index(name = "ix_loan_product_tenant_status", columnList = "tenant_id, status"))
@Filter(name = TenantAwareEntity.FILTER, condition = TenantAwareEntity.CONDITION)
public class LoanProduct extends TenantAwareEntity {

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    /** Decimal places of the currency: 2 for KES/USD/LKR, 0 for UGX/JPY. */
    @Column(name = "currency_scale", nullable = false)
    private int currencyScale = 2;

    @Column(name = "min_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal minPrincipal;

    @Column(name = "max_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxPrincipal;

    @Column(name = "default_principal", precision = 19, scale = 4)
    private BigDecimal defaultPrincipal;

    /** Rates are always stored as a nominal annual percentage, whatever the quoting basis. */
    @Column(name = "min_annual_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal minAnnualRate;

    @Column(name = "max_annual_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal maxAnnualRate;

    @Column(name = "default_annual_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal defaultAnnualRate;

    /** How the institution quotes the rate to borrowers; presentation only. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rate_quotation", nullable = false, length = 16)
    private RateQuotation rateQuotation = RateQuotation.ANNUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false, length = 24)
    private InterestMethod interestMethod = InterestMethod.REDUCING_BALANCE;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_frequency", nullable = false, length = 16)
    private RepaymentFrequency repaymentFrequency = RepaymentFrequency.MONTHLY;

    @Column(name = "min_installments", nullable = false)
    private int minInstallments;

    @Column(name = "max_installments", nullable = false)
    private int maxInstallments;

    @Column(name = "default_installments", nullable = false)
    private int defaultInstallments;

    @Enumerated(EnumType.STRING)
    @Column(name = "grace_type", nullable = false, length = 24)
    private GraceType graceType = GraceType.NONE;

    @Column(name = "max_grace_periods", nullable = false)
    private int maxGracePeriods;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 24)
    private FeeType feeType = FeeType.FLAT_AMOUNT;

    @Column(name = "fee_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal feeValue = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_collection", nullable = false, length = 32)
    private FeeCollection feeCollection = FeeCollection.DEDUCT_FROM_DISBURSEMENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_count", nullable = false, length = 16)
    private DayCountConvention dayCount = DayCountConvention.ACTUAL_365;

    // ---- Arrears policy, applied by the loan account service ----

    /** Annual penalty rate applied to the overdue amount. */
    @Column(name = "penalty_annual_rate", nullable = false, precision = 9, scale = 4)
    private BigDecimal penaltyAnnualRate = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "penalty_basis", nullable = false, length = 32)
    private PenaltyBasis penaltyBasis = PenaltyBasis.OVERDUE_TOTAL;

    /** Days after the due date before a penalty starts to accrue. */
    @Column(name = "penalty_grace_days", nullable = false)
    private int penaltyGraceDays;

    /** Days past due at which the loan is classified as defaulted. */
    @Column(name = "days_to_default", nullable = false)
    private int daysToDefault = 90;

    /** Number of sequential approvals required before disbursement. */
    @Column(name = "approval_levels", nullable = false)
    private int approvalLevels = 1;

    @Column(name = "requires_collateral", nullable = false)
    private boolean requiresCollateral;

    @Column(name = "requires_guarantor", nullable = false)
    private boolean requiresGuarantor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ProductStatus status = ProductStatus.DRAFT;

    protected LoanProduct() {
    }

    public LoanProduct(String code, String name, String currency) {
        this.code = code;
        this.name = name;
        this.currency = currency;
    }

    /**
     * Checks a proposed loan against this product's policy.
     *
     * <p>Collected into a single exception listing every breach, because rejecting one rule at a
     * time makes a loan officer re-submit repeatedly.</p>
     */
    public void validateTerms(BigDecimal principal, BigDecimal annualRate, int installments,
                              int gracePeriods) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (status != ProductStatus.ACTIVE) {
            problems.add("product '" + code + "' is not active");
        }
        if (principal == null || principal.compareTo(minPrincipal) < 0
                || principal.compareTo(maxPrincipal) > 0) {
            problems.add("principal must be between " + minPrincipal + " and " + maxPrincipal);
        }
        if (annualRate == null || annualRate.compareTo(minAnnualRate) < 0
                || annualRate.compareTo(maxAnnualRate) > 0) {
            problems.add("annual interest rate must be between " + minAnnualRate
                    + "% and " + maxAnnualRate + "%");
        }
        if (installments < minInstallments || installments > maxInstallments) {
            problems.add("number of installments must be between " + minInstallments
                    + " and " + maxInstallments);
        }
        if (gracePeriods > maxGracePeriods) {
            problems.add("grace periods must not exceed " + maxGracePeriods);
        }
        if (gracePeriods > 0 && graceType == GraceType.NONE) {
            problems.add("this product does not offer a grace period");
        }
        if (!problems.isEmpty()) {
            throw new ApiExceptions.BusinessRuleException(
                    "The requested terms breach product policy: " + String.join("; ", problems));
        }
    }

    /** Guards the configuration itself, so an incoherent product can never be saved. */
    public void validateConfiguration() {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (minPrincipal.compareTo(maxPrincipal) > 0) {
            problems.add("minimum principal exceeds maximum principal");
        }
        if (minAnnualRate.compareTo(maxAnnualRate) > 0) {
            problems.add("minimum rate exceeds maximum rate");
        }
        if (defaultAnnualRate.compareTo(minAnnualRate) < 0
                || defaultAnnualRate.compareTo(maxAnnualRate) > 0) {
            problems.add("default rate falls outside the configured range");
        }
        if (minInstallments > maxInstallments) {
            problems.add("minimum installments exceed maximum installments");
        }
        if (defaultInstallments < minInstallments || defaultInstallments > maxInstallments) {
            problems.add("default installments fall outside the configured range");
        }
        if (defaultPrincipal != null && (defaultPrincipal.compareTo(minPrincipal) < 0
                || defaultPrincipal.compareTo(maxPrincipal) > 0)) {
            problems.add("default principal falls outside the configured range");
        }
        if (maxGracePeriods >= maxInstallments) {
            problems.add("grace periods must be fewer than the maximum number of installments");
        }
        if (approvalLevels < 1 || approvalLevels > 5) {
            problems.add("approval levels must be between 1 and 5");
        }
        if (!problems.isEmpty()) {
            throw new ApiExceptions.BusinessRuleException(
                    "Product configuration is invalid: " + String.join("; ", problems));
        }
    }

    public void activate() {
        validateConfiguration();
        this.status = ProductStatus.ACTIVE;
    }

    public void deactivate() {
        // Existing loans are unaffected; this only stops new applications.
        this.status = ProductStatus.INACTIVE;
    }

    public void configureAmounts(BigDecimal min, BigDecimal max, BigDecimal defaultAmount) {
        this.minPrincipal = min;
        this.maxPrincipal = max;
        this.defaultPrincipal = defaultAmount;
    }

    public void configureRates(BigDecimal min, BigDecimal max, BigDecimal defaultRate,
                               RateQuotation quotation, InterestMethod method) {
        this.minAnnualRate = min;
        this.maxAnnualRate = max;
        this.defaultAnnualRate = defaultRate;
        this.rateQuotation = quotation;
        this.interestMethod = method;
    }

    public void configureTerm(RepaymentFrequency frequency, int min, int max, int defaultCount,
                              GraceType graceType, int maxGracePeriods) {
        this.repaymentFrequency = frequency;
        this.minInstallments = min;
        this.maxInstallments = max;
        this.defaultInstallments = defaultCount;
        this.graceType = graceType;
        this.maxGracePeriods = maxGracePeriods;
    }

    public void configureFees(FeeType type, BigDecimal value, FeeCollection collection) {
        this.feeType = type;
        this.feeValue = value;
        this.feeCollection = collection;
    }

    public void configurePenalties(BigDecimal annualRate, PenaltyBasis basis, int graceDays,
                                   int daysToDefault) {
        this.penaltyAnnualRate = annualRate;
        this.penaltyBasis = basis;
        this.penaltyGraceDays = graceDays;
        this.daysToDefault = daysToDefault;
    }

    public void configureWorkflow(int approvalLevels, boolean requiresCollateral,
                                  boolean requiresGuarantor) {
        this.approvalLevels = approvalLevels;
        this.requiresCollateral = requiresCollateral;
        this.requiresGuarantor = requiresGuarantor;
    }

    public void describe(String name, String description, int currencyScale,
                         DayCountConvention dayCount) {
        this.name = name;
        this.description = description;
        this.currencyScale = currencyScale;
        this.dayCount = dayCount;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCurrency() {
        return currency;
    }

    public int getCurrencyScale() {
        return currencyScale;
    }

    public BigDecimal getMinPrincipal() {
        return minPrincipal;
    }

    public BigDecimal getMaxPrincipal() {
        return maxPrincipal;
    }

    public BigDecimal getDefaultPrincipal() {
        return defaultPrincipal;
    }

    public BigDecimal getMinAnnualRate() {
        return minAnnualRate;
    }

    public BigDecimal getMaxAnnualRate() {
        return maxAnnualRate;
    }

    public BigDecimal getDefaultAnnualRate() {
        return defaultAnnualRate;
    }

    public RateQuotation getRateQuotation() {
        return rateQuotation;
    }

    public InterestMethod getInterestMethod() {
        return interestMethod;
    }

    public RepaymentFrequency getRepaymentFrequency() {
        return repaymentFrequency;
    }

    public int getMinInstallments() {
        return minInstallments;
    }

    public int getMaxInstallments() {
        return maxInstallments;
    }

    public int getDefaultInstallments() {
        return defaultInstallments;
    }

    public GraceType getGraceType() {
        return graceType;
    }

    public int getMaxGracePeriods() {
        return maxGracePeriods;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public BigDecimal getFeeValue() {
        return feeValue;
    }

    public FeeCollection getFeeCollection() {
        return feeCollection;
    }

    public DayCountConvention getDayCount() {
        return dayCount;
    }

    public BigDecimal getPenaltyAnnualRate() {
        return penaltyAnnualRate;
    }

    public PenaltyBasis getPenaltyBasis() {
        return penaltyBasis;
    }

    public int getPenaltyGraceDays() {
        return penaltyGraceDays;
    }

    public int getDaysToDefault() {
        return daysToDefault;
    }

    public int getApprovalLevels() {
        return approvalLevels;
    }

    public boolean isRequiresCollateral() {
        return requiresCollateral;
    }

    public boolean isRequiresGuarantor() {
        return requiresGuarantor;
    }

    public ProductStatus getStatus() {
        return status;
    }
}
