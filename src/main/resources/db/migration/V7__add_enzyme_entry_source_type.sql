ALTER TABLE enzyme_entry
  ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'NCBI_IMPORT' AFTER description;

CREATE INDEX idx_enzyme_source_type_created_at
  ON enzyme_entry (source_type, created_at);
