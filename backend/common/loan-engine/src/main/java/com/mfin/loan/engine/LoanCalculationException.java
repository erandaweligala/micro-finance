package com.mfin.loan.engine;

/**
 * Raised for structurally invalid calculation input. Services translate this into a
 * {@code 422 Unprocessable Entity} with the {@code LOAN_CALCULATION_INVALID} error code.
 */
public class LoanCalculationException extends RuntimeException {

    private final String field;

    public LoanCalculationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
