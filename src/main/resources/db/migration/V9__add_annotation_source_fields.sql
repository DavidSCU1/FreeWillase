ALTER TABLE enzyme_annotation
  ADD COLUMN source_db VARCHAR(32) NULL AFTER description,
  ADD COLUMN source_ref VARCHAR(255) NULL AFTER source_db;

CREATE INDEX idx_enzyme_annotation_source
  ON enzyme_annotation (enzyme_id, source_db, source_ref(128));
