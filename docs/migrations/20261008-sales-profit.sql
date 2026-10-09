-- Apply before deploying when Hibernate ddl-auto=validate/none. Local ddl-auto=update creates this column.
ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS unit_cost_at_sale NUMERIC(19,6);
-- Deliberately leave existing invoices NULL: today's product cost cannot reconstruct historical costs.
