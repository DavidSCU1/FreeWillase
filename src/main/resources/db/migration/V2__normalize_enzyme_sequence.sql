-- Phase 1 normalization:
-- 1. Keep enzyme_entry focused on entry-level metadata.
-- 2. Use enzyme_sequence as the canonical storage for sequence payload.

ALTER TABLE enzyme_sequence
  ADD INDEX idx_sequence_enzyme_primary (enzyme_id, is_primary);

ALTER TABLE enzyme_sequence
  ADD INDEX idx_sequence_hash (sequence_hash);

ALTER TABLE enzyme_sequence
  ADD CONSTRAINT fk_enzyme_sequence_entry
    FOREIGN KEY (enzyme_id) REFERENCES enzyme_entry(id)
    ON DELETE CASCADE;
