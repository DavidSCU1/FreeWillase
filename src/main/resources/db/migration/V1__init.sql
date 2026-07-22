-- FreeWillase Database Schema

-- System User
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(128) NOT NULL,
  email VARCHAR(128),
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Enzyme Entry
CREATE TABLE IF NOT EXISTS enzyme_entry (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL UNIQUE,
  protein_accession VARCHAR(64),
  protein_version VARCHAR(16),
  gene_id VARCHAR(64),
  gene_symbol VARCHAR(128),
  locus_tag VARCHAR(128),
  tax_id VARCHAR(64),
  name VARCHAR(255) NOT NULL,
  ec_number VARCHAR(64),
  organism VARCHAR(255),
  substrate VARCHAR(255),
  product VARCHAR(255),
  optimal_ph VARCHAR(64),
  optimal_temperature VARCHAR(64),
  description TEXT,
  status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_protein_accession_version (protein_accession, protein_version),
  INDEX idx_enzyme_ec (ec_number),
  INDEX idx_enzyme_name (name)
);

-- Enzyme Sequence
CREATE TABLE IF NOT EXISTS enzyme_sequence (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  enzyme_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  sequence_text LONGTEXT NOT NULL,
  sequence_length INT NOT NULL,
  sequence_hash VARCHAR(128) NOT NULL,
  is_primary TINYINT NOT NULL DEFAULT 1,
  source_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_enzyme_version (enzyme_id, version_no)
);

-- NCBI Import Task
CREATE TABLE IF NOT EXISTS ncbi_import_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_name VARCHAR(255) NOT NULL,
  source_type VARCHAR(16) NOT NULL,
  total_count INT NOT NULL DEFAULT 0,
  success_count INT NOT NULL DEFAULT 0,
  failed_count INT NOT NULL DEFAULT 0,
  duplicate_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finished_at DATETIME
);

-- NCBI Import Task Item
CREATE TABLE IF NOT EXISTS ncbi_import_task_item (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_id BIGINT NOT NULL,
  accession VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  enzyme_id BIGINT,
  message TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_import_item_task (task_id)
);

-- Literature Record
CREATE TABLE IF NOT EXISTS literature_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  title VARCHAR(512) NOT NULL,
  authors TEXT,
  journal VARCHAR(255),
  publish_year INT,
  doi VARCHAR(128),
  pmid VARCHAR(64),
  keywords TEXT,
  abstract_text LONGTEXT,
  source_db VARCHAR(64) NOT NULL DEFAULT 'NCBI',
  source_url VARCHAR(512),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_literature_doi (doi),
  INDEX idx_literature_pmid (pmid)
);

-- Literature Relation
CREATE TABLE IF NOT EXISTS literature_relation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  literature_id BIGINT NOT NULL,
  enzyme_id BIGINT,
  relation_type VARCHAR(32) NOT NULL,
  confidence_level VARCHAR(16) NOT NULL DEFAULT 'CANDIDATE',
  confidence_score DECIMAL(8,2),
  matched_fields VARCHAR(255),
  note TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
