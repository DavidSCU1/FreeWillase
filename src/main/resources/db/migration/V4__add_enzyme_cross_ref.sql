CREATE TABLE IF NOT EXISTS enzyme_cross_ref (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enzyme_id BIGINT NOT NULL,
  ref_db VARCHAR(32) NOT NULL,
  ref_type VARCHAR(32) NOT NULL,
  ref_value VARCHAR(255) NOT NULL,
  ref_url VARCHAR(512),
  is_primary TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enzyme_cross_ref (enzyme_id, ref_db, ref_type, ref_value),
  INDEX idx_cross_ref_enzyme_primary (enzyme_id, ref_db, is_primary),
  INDEX idx_cross_ref_lookup (ref_db, ref_value),
  CONSTRAINT fk_enzyme_cross_ref_entry
    FOREIGN KEY (enzyme_id) REFERENCES enzyme_entry(id)
    ON DELETE CASCADE
);
