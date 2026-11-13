-- Human-readable document numbers. Transaction IDs, bank references and amounts stay unchanged.
CREATE SEQUENCE wallet_document_number_seq AS BIGINT START WITH 1;
ALTER TABLE wallet_transactions ADD COLUMN previous_invoice_id VARCHAR(50);
ALTER TABLE wallet_transactions ADD COLUMN previous_document_number VARCHAR(64);

-- Preserve previously displayed labels and the distinction between legacy and canonical records.
UPDATE wallet_transactions
SET previous_invoice_id = invoice_id,
    previous_document_number = document_number,
    document_number = NULL;

WITH numbered AS (
    SELECT id,
           TO_CHAR(created_at AT TIME ZONE 'Asia/Kolkata', 'YYYY') AS document_year,
           ROW_NUMBER() OVER (ORDER BY created_at, id) AS serial
    FROM wallet_transactions
), labels AS (
    SELECT id, 'INV-' || document_year || '-' ||
           LPAD(serial::TEXT, GREATEST(6, LENGTH(serial::TEXT)), '0') AS label
    FROM numbered
)
UPDATE wallet_transactions t
SET invoice_id = labels.label,
    document_number = CASE WHEN t.previous_document_number IS NOT NULL THEN labels.label ELSE NULL END
FROM labels WHERE t.id = labels.id;

CREATE UNIQUE INDEX uq_wallet_invoice_number ON wallet_transactions(invoice_id) WHERE invoice_id IS NOT NULL;

SELECT SETVAL('wallet_document_number_seq', GREATEST(COUNT(*), 1), COUNT(*) > 0)
FROM wallet_transactions;
