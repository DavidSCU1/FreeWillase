ALTER TABLE literature_record
ADD COLUMN attachment_status VARCHAR(32) NOT NULL DEFAULT 'NONE' AFTER source_url,
ADD COLUMN attachment_file_name VARCHAR(255) NULL AFTER attachment_status,
ADD COLUMN attachment_path VARCHAR(512) NULL AFTER attachment_file_name,
ADD COLUMN attachment_content_type VARCHAR(128) NULL AFTER attachment_path,
ADD COLUMN attachment_size BIGINT NULL AFTER attachment_content_type,
ADD COLUMN attachment_source_url VARCHAR(512) NULL AFTER attachment_size;
