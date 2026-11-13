# Platform fee GST and escrow

New hospital shift posts reserve clinician gross wages, an 8% platform fee, and 18% GST on that platform fee. GST is not applied to clinician wages. Rates and GST amounts are saved on the shift. Each booking's fee and GST are rounded to two decimal places before multiplication by openings, matching settlement and frontend paise calculations.

Example: wages Rs 3,600 + platform fee Rs 288 + GST Rs 51.84 = Rs 3,939.84 reserved. The hospital's first completed shift has both fee and GST refunded to its available wallet balance. Later completed shifts retain both. Clinician platform deductions remain zero; existing TDS is separate.

V22 adds zero-default GST fields to existing shifts, nullable settlement GST to claims, and nullable fee/GST breakdowns to wallet transactions. Existing reservations and completed records are not retroactively taxed. Edits preserve each shift's stored tax rate. Restart the backend to apply Flyway migration V22 before using the updated fee preview. The frontend blocks new posting when the server's fee/GST policy is unavailable.

Edits, cancellation, rejection, expiry, short duty, overtime, dispute reconciliation and payout disbursement include the saved GST. Completion refunds the first shift's reserved fee and GST under the existing wallet lock. Settlement claims retain their actual fee/GST snapshots for later disbursement. Dispute refunds remove the refunded opening instead of publishing an unfunded slot. Reserve mismatches and attempts to refund disbursed payouts fail rather than crediting unrelated escrow.

Wallet statements and PDF exports use recorded transaction fee/GST fields; they do not divide total wages by 1.18 to invent tax. Signed fee/GST reconciliation adjustments remain signed. Historical transactions without GST records show no inferred tax. Statements do not fabricate GST registration identifiers, SAC classifications, CGST/SGST splits or input tax credit eligibility. Formal tax invoices and tax remittance require separately configured issuer and supply details; this change implements the agreed fee/GST accounting and statement presentation.
