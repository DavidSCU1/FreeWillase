CREATE TABLE IF NOT EXISTS enzyme_annotation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enzyme_id BIGINT NOT NULL,
  annotation_type VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  start_residue INT NOT NULL,
  end_residue INT NOT NULL,
  chain_label VARCHAR(32),
  mutation_label VARCHAR(128),
  color_hex VARCHAR(16) NOT NULL DEFAULT '#3B82F6',
  description TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_enzyme_annotation_enzyme (enzyme_id),
  INDEX idx_enzyme_annotation_type (annotation_type)
);
