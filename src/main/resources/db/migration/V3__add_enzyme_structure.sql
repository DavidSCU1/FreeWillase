CREATE TABLE IF NOT EXISTS enzyme_structure (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enzyme_id BIGINT NOT NULL,
  structure_type VARCHAR(32) NOT NULL,
  structure_id VARCHAR(128),
  source_db VARCHAR(64) NOT NULL,
  source_url VARCHAR(512),
  is_primary TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enzyme_structure (enzyme_id, structure_type, structure_id),
  INDEX idx_structure_enzyme_primary (enzyme_id, is_primary),
  INDEX idx_structure_lookup (source_db, structure_id),
  CONSTRAINT fk_enzyme_structure_entry
    FOREIGN KEY (enzyme_id) REFERENCES enzyme_entry(id)
    ON DELETE CASCADE
);
