-- GST accounting foundation, PostgreSQL. Apply before starting ddl-auto=validate/none.
-- Local/QA/desktop ddl-auto=update creates the mapped schema on backend restart.
-- Apply earlier module migrations first on older installations. No historical amounts are backfilled.
BEGIN;

ALTER TABLE products ADD COLUMN IF NOT EXISTS tax_category VARCHAR(20);
ALTER TABLE products ADD COLUMN IF NOT EXISTS unit_code VARCHAR(8);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_document_number VARCHAR(16);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS customer_gstin VARCHAR(15);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS customer_address VARCHAR(1000);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS delivery_address VARCHAR(1000);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS place_of_supply VARCHAR(2);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_registration_mode VARCHAR(20);
ALTER TABLE bills ADD COLUMN IF NOT EXISTS tax_price_mode VARCHAR(12);
CREATE UNIQUE INDEX IF NOT EXISTS uk_bills_tenant_tax_number ON bills(tenant_id, tax_document_number);

ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS cgst_amount DOUBLE PRECISION;
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS sgst_amount DOUBLE PRECISION;
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS igst_amount DOUBLE PRECISION;
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS final_discount_amount DOUBLE PRECISION;
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS tax_category VARCHAR(20);
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS unit_code VARCHAR(8);

ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS taxable_amount DOUBLE PRECISION;
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS gst_amount DOUBLE PRECISION;
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS cgst_amount DOUBLE PRECISION;
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS sgst_amount DOUBLE PRECISION;
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS igst_amount DOUBLE PRECISION;
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS gst_rate DOUBLE PRECISION;
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS hsn_code VARCHAR(8);
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS unit_code VARCHAR(8);
ALTER TABLE purchase_bill_items ADD COLUMN IF NOT EXISTS tax_category VARCHAR(20);

CREATE TABLE IF NOT EXISTS gst_settings (
    tenant_id VARCHAR(100) PRIMARY KEY,
    version BIGINT,
    registration_mode VARCHAR(20) NOT NULL,
    gstin VARCHAR(15), legal_name VARCHAR(160), address VARCHAR(1000), state_code VARCHAR(2),
    effective_from DATE, price_mode VARCHAR(12), invoice_prefix VARCHAR(4)
);
CREATE TABLE IF NOT EXISTS gst_documents (
    id BIGSERIAL PRIMARY KEY,
    tenant_id VARCHAR(100) NOT NULL, source_key VARCHAR(120) NOT NULL,
    document_type VARCHAR(30) NOT NULL, document_number VARCHAR(64) NOT NULL,
    document_date DATE NOT NULL, source_id BIGINT, original_document_id BIGINT,
    original_document_number VARCHAR(64), registration_mode VARCHAR(20),
    store_gstin VARCHAR(15), store_name VARCHAR(160), store_address VARCHAR(1000), store_state VARCHAR(2),
    party_name VARCHAR(160), party_gstin VARCHAR(15), party_address VARCHAR(1000), delivery_address VARCHAR(1000),
    place_of_supply VARCHAR(2), price_mode VARCHAR(12), reason VARCHAR(1000), request_fingerprint VARCHAR(64),
    taxable_amount NUMERIC(19,2), cgst_amount NUMERIC(19,2), sgst_amount NUMERIC(19,2),
    igst_amount NUMERIC(19,2), total_amount NUMERIC(19,2),
    recorded_at TIMESTAMP NOT NULL, actor_user_id VARCHAR(160),
    itc_status VARCHAR(16), review_notes VARCHAR(1000), supplier_note_number VARCHAR(64),
    supplier_note_date DATE, reviewed_at TIMESTAMP, reviewed_by VARCHAR(160),
    CONSTRAINT uk_gst_document_source UNIQUE (tenant_id, source_key)
);
CREATE INDEX IF NOT EXISTS idx_gst_documents_tenant_date ON gst_documents(tenant_id, document_date);
CREATE TABLE IF NOT EXISTS gst_document_lines (
    document_id BIGINT NOT NULL REFERENCES gst_documents(id), line_index INTEGER NOT NULL,
    source_item_id BIGINT, product_id BIGINT, product_name VARCHAR(255), barcode VARCHAR(64),
    hsn_code VARCHAR(8), unit_code VARCHAR(8), tax_category VARCHAR(20),
    quantity NUMERIC(19,2), gst_rate NUMERIC(9,6), taxable_amount NUMERIC(19,2),
    cgst_amount NUMERIC(19,2), sgst_amount NUMERIC(19,2), igst_amount NUMERIC(19,2),
    discount_amount NUMERIC(19,2), PRIMARY KEY (document_id, line_index)
);
CREATE TABLE IF NOT EXISTS gst_periods (
    id BIGSERIAL PRIMARY KEY, tenant_id VARCHAR(100) NOT NULL, period_month VARCHAR(7) NOT NULL,
    locked BOOLEAN NOT NULL, notes VARCHAR(1000), changed_at TIMESTAMP, changed_by VARCHAR(160),
    CONSTRAINT uk_gst_period_month UNIQUE (tenant_id, period_month)
);
CREATE TABLE IF NOT EXISTS gst_audit (
    id BIGSERIAL PRIMARY KEY, tenant_id VARCHAR(100) NOT NULL, action VARCHAR(40) NOT NULL,
    reference VARCHAR(120), details TEXT, recorded_at TIMESTAMP, actor_user_id VARCHAR(160)
);
COMMIT;
