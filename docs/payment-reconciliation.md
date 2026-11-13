# Payment reconciliation and document history

Hospital deposits are requests, not automatic credits. Submit a real 8-30 character bank reference. In Admin > Applications > Finance Review, match the received bank amount, reference and payer against independent bank evidence, enter reconciliation notes and approve or reject. Approval credits once under a database lock. The portal does not execute or confirm a bank transfer itself.

Bank and UPI ownership are reviewed manually in the same Finance Review tab. Admins must independently check ownership evidence; masked details and notes are not a bank verification integration. Updating details clears verification. Review uses the saved updatedAt to reject stale details. Legacy Verified rows without a verification timestamp must be reviewed again.

Payout release requires a verified destination with a verification timestamp and a real external bank transfer UTR. Only record release after checking the transfer. Paid claims save the masked destination, identity, registration and actual disbursement timestamp. Subsequent destination changes do not rewrite paid receipts. One claim settlement document advances from pending to paid, avoiding duplicate charges in new records. Pending advice never proves payment.

Candidate amounts come from stored gross, fee, TDS and net values, preserving paise. Applications, rejections and no-shows do not contribute earnings. Documents report stored withholding; they do not certify tax filing or deposit. Hospital downloads are wallet statements, not statutory GST invoices. Formal invoicing still needs verified issuer, supply and tax particulars and a separate compliance workflow.

V23 preserves historical balances and transactions. Historical queued duplicates are omitted from Statements but retained in Activity. Missing historical bank reconciliation, destinations and tax snapshots are not invented. Existing balances must be reconciled against real bank records by an operator before live financial use; this migration does not retroactively establish that funds were received.

## Bank encryption and rollout

BANK_ACCOUNT_ENCRYPTION_KEY must be a base64 encoded, randomly generated 32-byte key in production. Supply it through the deployment secret manager and securely back it up. Never commit or log it. Restoring the database requires the same key. Do not rotate by simply replacing it: existing ciphertext must be re-encrypted using both keys in a controlled migration.

Only dev/test may create a persistent local .local-secrets/bank-account.key (gitignored). Keep this file across local restarts; deleting it makes encrypted account records unreadable. At startup the legacy encryption runner converts old plaintext/ENC_ bank account columns to authenticated AES-256-GCM ciphertext in a transaction. API responses return masked account details only. Bank/UPI details must not be included in operational logs.

Restart the backend after deploying to apply V23 and initialize encryption. Confirm the correct environment key before starting. No running developer service is restarted by this change.

## Document numbers

V24 replaces UUID-style wallet document labels with INV-YYYY-000001 numbers. The year follows Asia/Kolkata. A PostgreSQL sequence allocates the serial globally across years and concurrent requests; numbers are never reused and updates retain the original number. Rolled-back attempts may leave gaps. The same saved number is returned to the wallet list, preview and PDF filename/content. This numbering does not change the document classification into a statutory tax invoice.

Existing labels are preserved in previous_invoice_id and previous_document_number for tracing. Transaction UUIDs, bank UTRs, amounts and the legacy/canonical record distinction remain unchanged. Restart the backend to apply V24, then refresh the wallet.
