# 5. Loan Calculation Rules and Worked Examples

Every figure in this document was produced by running the shipped engine
(`backend/common/loan-engine`). They are not illustrative approximations — they are the exact
values the API returns, and the unit tests assert them.

## 5.1 Principles

1. **`BigDecimal` only.** `double` cannot represent `0.01`. Over a 240-month loan that error
   compounds into a real discrepancy on a real customer's balance.
2. **Intermediate precision, published rounding.** Arithmetic runs at 34 significant digits
   (`MathContext(34, HALF_EVEN)`); every amount that leaves the engine is rounded to the
   currency's scale with `HALF_UP`.
3. **The last installment absorbs the residue.** A level installment rarely divides a loan
   exactly. Rather than distributing the difference or leaving a fraction outstanding, the
   final installment is adjusted so the schedule closes at **exactly zero**.
4. **One engine, everywhere.** The quotation shown to a customer, the preview on an
   application, and the schedule generated at disbursement all come from this same code. Two
   implementations would drift on rounding alone.
5. **Zero-decimal currencies are supported.** `currencyScale` of 0 (UGX, JPY) or 2 (KES, USD).

## 5.2 Reducing balance

Interest is charged on the balance still owed, so it falls as the loan is repaid.

```
periodicRate  i = annualRate / periodsPerYear / 100
installment     = P × i × (1 + i)^n ÷ ((1 + i)^n − 1)        for i > 0
installment     = P ÷ n                                      for i = 0
```

Each period: `interest = round(outstandingBalance × i)`, and `principal = installment −
interest`. The final row sets `principal = remaining balance`.

### Worked example — 100,000 at 12% p.a. over 12 monthly installments

`i = 12 / 12 / 100 = 0.01`

| | |
|---|---|
| Monthly installment | **8,884.88** |
| Total interest | **6,618.53** |
| Total repayable | **106,618.53** |
| Maturity | 15 Jan 2027 |

| # | Due date | Opening | Principal | Interest | Total due | Closing |
|--:|---|--:|--:|--:|--:|--:|
| 1 | 2026-02-15 | 100,000.00 | 7,884.88 | 1,000.00 | 8,884.88 | 92,115.12 |
| 2 | 2026-03-15 | 92,115.12 | 7,963.73 | 921.15 | 8,884.88 | 84,151.39 |
| 3 | 2026-04-15 | 84,151.39 | 8,043.37 | 841.51 | 8,884.88 | 76,108.02 |
| … | | | | | | |
| 12 | 2027-01-15 | 8,796.88 | 8,796.88 | 87.97 | **8,884.85** | **0.00** |

Note the final installment: **8,884.85**, three cents below the level payment. That is the
rounding residue, deliberately concentrated in the last row so the loan closes exactly.

> A common mistake is to report total interest as `installment × n − principal`
> (`8,884.88 × 12 − 100,000 = 6,618.56`). That over-states interest by three cents, because it
> assumes twelve identical payments. The engine sums the *actual* per-period interest:
> **6,618.53**.

## 5.3 Flat rate

Interest is charged on the original principal for the whole term, regardless of repayment.

```
years          = n / periodsPerYear
totalInterest  = P × (annualRate / 100) × years
installment    = (P + totalInterest) / n
```

### Worked example — 100,000 at 12% flat over 12 monthly installments

| | |
|---|---|
| Total interest | **12,000.00** (100,000 × 12% × 1 year) |
| Monthly installment | **9,333.33** |
| Total repayable | **112,000.00** |

| # | Due date | Principal | Interest | Total due | Closing |
|--:|---|--:|--:|--:|--:|
| 1 | 2026-02-15 | 8,333.33 | 1,000.00 | 9,333.33 | 91,666.67 |
| 2 | 2026-03-15 | 8,333.33 | 1,000.00 | 9,333.33 | 83,333.34 |
| 12 | 2027-01-15 | 8,333.37 | 1,000.00 | 9,333.37 | 0.00 |

**Flat rate is materially more expensive than it appears.** At the same headline 12%, flat
costs 12,000 against reducing balance's 6,618.53 — roughly 1.8× — because interest is charged
on the full principal even in the final month when almost nothing is owed. Its effective APR
is close to 21.5%. Where consumer-credit rules require an effective rate to be disclosed, this
is the figure that must be shown, not the 12%.

## 5.4 Zero-interest loans

Handled explicitly rather than falling out of the formula — the annuity expression divides by
`(1+i)^n − 1`, which is zero when `i = 0`.

### Worked example — 60,000 at 0% over 6 monthly installments

| | |
|---|---|
| Monthly installment | **10,000.00** |
| Total interest | **0.00** |
| Total repayable | **60,000.00** |

Where the principal does not divide evenly (1,000 over 3 months), the residue again lands on
the final row: `333.33, 333.33, 333.34`.

## 5.5 Grace periods

Two kinds, both configured per product:

- **`PRINCIPAL_GRACE`** — interest-only. The borrower pays interest during the grace period;
  principal amortisation is compressed into the remaining installments.
- **`FULL_GRACE`** — full moratorium. Nothing is collected and accrued interest is
  **capitalised** into the principal, so the borrower afterwards owes more than they borrowed.

### Worked example — 100,000 at 12% over 12 months, 3-month interest-only grace

| | |
|---|---|
| Installment after grace | **11,674.04** (vs 8,884.88 without) |
| Total interest | **8,066.32** (vs 6,618.53) |
| Total repayable | **108,066.32** |

| # | Due date | Principal | Interest | Total due | Closing |
|--:|---|--:|--:|--:|--:|
| 1 | 2026-02-15 | 0.00 | 1,000.00 | 1,000.00 | 100,000.00 |
| 2 | 2026-03-15 | 0.00 | 1,000.00 | 1,000.00 | 100,000.00 |
| 3 | 2026-04-15 | 0.00 | 1,000.00 | 1,000.00 | 100,000.00 |
| 4 | 2026-05-15 | 10,674.04 | 1,000.00 | 11,674.04 | 89,325.96 |
| 12 | 2027-01-15 | 11,558.42 | 115.58 | 11,674.00 | 0.00 |

A grace period is not free: deferring principal for three months costs this borrower
**1,447.79** in additional interest and raises every later installment by 31%. The calculator
screen shows both figures so that trade-off is visible before the loan is written.

Under `FULL_GRACE` the balance *grows* during the moratorium — 100,000 becomes 103,030.10 after
three months at 1%/month — which is why the schedule's closing balance may exceed its opening
balance in those rows.

## 5.6 Non-monthly frequencies

`periodsPerYear`: weekly 52, fortnightly 26, monthly 12, quarterly 4, semi-annual 2, annual 1.

### Worked example — 50,000 at 24% p.a. over 8 weekly installments

`i = 24 / 52 / 100 = 0.004615…`

| | |
|---|---|
| Weekly installment | **6,380.51** |
| Total interest | **1,044.05** |
| Maturity | 12 Mar 2026 |

| # | Due date | Principal | Interest | Total due | Closing |
|--:|---|--:|--:|--:|--:|
| 1 | 2026-01-22 | 6,149.74 | 230.77 | 6,380.51 | 43,850.26 |
| 2 | 2026-01-29 | 6,178.12 | 202.39 | 6,380.51 | 37,672.14 |
| 8 | 2026-03-12 | 6,351.17 | 29.31 | 6,380.48 | 0.00 |

## 5.7 Partial (broken) first periods

When the first repayment falls later than one full period after disbursement, the extra days
attract **stub interest**, charged on top of the first installment.

```
stubDays      = dayCount.days(naturalFirstDueDate, requestedFirstDueDate)
stubInterest  = P × (annualRate / 100) × stubDays / daysInYear
```

Day-count conventions: `ACTUAL_365`, `ACTUAL_360`, `THIRTY_360`.

### Worked example — disbursed 15 Jan 2026, first repayment 1 Mar 2026

The natural first due date is 15 Feb; the requested date is 14 days later.

```
stubInterest = 100,000 × 0.12 × 14/365 = 460.27
```

| | |
|---|---|
| Broken-period interest | **460.27** |
| First installment | **9,345.15** (8,884.88 + 460.27) |
| Subsequent installments | 8,884.88 |
| Total interest | **7,078.80** |

Critically, the stub is added to the *amount due*, not subtracted from the principal component:
principal in installment 1 remains 7,884.88. Deducting it from principal instead would cause
**negative amortisation** — the balance rising rather than falling — which is exactly the
behaviour that gets lenders into regulatory trouble.

## 5.8 Processing fees

| Collection | Effect |
|---|---|
| `DEDUCT_FROM_DISBURSEMENT` | Netted off the cash released; the borrower still repays the full principal |
| `ADD_TO_FIRST_INSTALLMENT` | Added in full to installment 1 |
| `SPREAD_ACROSS_INSTALLMENTS` | Split evenly, residue on the last installment |

### Worked example — 2% fee deducted at disbursement

| | |
|---|---|
| Principal | 100,000.00 |
| Fee | **2,000.00** |
| **Cash the borrower receives** | **98,000.00** |
| Total repayable | 106,618.53 |

The customer receives 98,000 but repays interest on 100,000 — so their true cost is higher than
the 12% headline. The calculator surfaces `netDisbursedAmount` for exactly this reason.

## 5.9 Due-date generation

Due dates are always generated as *multiples of the period from a single anchor*, never by
stepping off the previous date.

Stepping causes month-end drift: a loan disbursed 31 January would step 28 Feb → 28 Mar →
28 Apr, silently moving the customer's payment day forward by three days for the rest of the
loan. Anchoring gives the correct 28 Feb → 31 Mar → 30 Apr.

*(This was a real bug caught by the test suite during development; the fix is in
`LoanCalculator.dueDates`.)*

## 5.10 Input validation

Rejected with a field-level 422 (`LOAN_CALCULATION_INVALID`):

| Rule | Reason |
|---|---|
| `principal > 0` | A zero or negative loan is meaningless |
| `0 ≤ annualRate ≤ 200` | Above 200% is almost always a fraction entered as a percent |
| `1 ≤ installments ≤ 600` | Bounds the amortisation loop |
| `firstRepaymentDate > disbursementDate` | A repayment cannot precede the loan |
| `gracePeriods < installments` | Grace cannot consume the whole tenor |
| `0 ≤ currencyScale ≤ 4` | Beyond this is not a currency |
| Installment must exceed period interest | Otherwise the loan can never amortise |

## 5.11 Repayment allocation

Separate from schedule generation: allocation decides where a *received* payment goes.

1. **Oldest debt first.** Installments already due are settled before any future one, so a
   borrower in arrears clears arrears rather than paying ahead.
2. **Bucket order within an installment**, configurable per institution:
   - `PENALTY_FEE_INTEREST_PRINCIPAL` (default — most jurisdictions)
   - `PRINCIPAL_INTEREST_FEE_PENALTY` (borrower-friendly)
   - `INTEREST_PRINCIPAL_FEE_PENALTY`
3. **Surplus pays ahead** into future installments in date order.
4. **True overpayment becomes a credit balance**, not a refund and not a silent absorption.

### Worked example

Two overdue installments (each 800 principal + 200 interest, plus 50 and 25 penalty) and two
future ones. A payment of **1,500** under the default order:

| Installment | Penalty | Interest | Principal | Total |
|---|--:|--:|--:|--:|
| 1 (overdue) | 50.00 | 200.00 | 800.00 | 1,050.00 |
| 2 (overdue) | 25.00 | 200.00 | 225.00 | 450.00 |
| **Total** | **75.00** | **400.00** | **1,025.00** | **1,500.00** |

Installment 1 is cleared entirely before installment 2 is touched; within each, penalties
outrank interest and interest outranks principal.

A payment of 5,000 against a 4,075 total balance applies 4,075 and records **925 as a credit
balance** — visible on the receipt as "held as credit", never quietly kept.

## 5.12 Penalty accrual

Run once daily rather than computed on read, because a penalty is a financial event that must
be dated, recorded and posted to the ledger — not a number that changes depending on when
someone happens to open a screen.

```
dailyRate = penaltyAnnualRate / 100 / 365
penalty   = basis × dailyRate            (per overdue installment, per day past the grace days)
```

`basis` is one of `OVERDUE_PRINCIPAL`, `OVERDUE_TOTAL` (default) or `INSTALLMENT_AMOUNT`.

## 5.13 Early settlement

The payoff quote charges outstanding principal plus interest and charges **accrued to the
settlement date** — not the remaining scheduled interest, which the borrower has not yet
incurred. Charging full scheduled interest on early settlement is prohibited in many
jurisdictions and is, in any case, indefensible.

## 5.14 Test coverage

`LoanCalculatorTest` — 46 tests covering: the annuity formula against a reference
implementation; monotonic interest/principal curves; single-installment and 240-month loans;
flat vs reducing comparisons; zero-interest under both methods; both grace types; broken
periods across all three day-count conventions; all three fee collection modes; every
frequency; zero-decimal currencies; month-end clamping; recurring-decimal rates; and every
validation rule.

`RepaymentAllocatorTest` — 16 tests covering all three allocation orders, oldest-first
settlement, partial/advance/excess payments, settled-installment skipping, and the invariant
that **every payment is accounted for exactly once**.

Every schedule-producing test also asserts a set of universal invariants: the balance walk is
continuous, components sum to the headline totals, and the loan closes at exactly zero.
