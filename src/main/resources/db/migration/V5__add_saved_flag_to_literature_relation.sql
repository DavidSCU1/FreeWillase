ALTER TABLE literature_relation
ADD COLUMN saved_to_library TINYINT(1) NOT NULL DEFAULT 1 AFTER note;
