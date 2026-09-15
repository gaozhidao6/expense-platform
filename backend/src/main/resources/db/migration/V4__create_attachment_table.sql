-- 附件元数据表。
-- 文件内容保存在本地目录或对象存储中，本表只保存文件索引和业务关联信息。
CREATE TABLE attachment (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '附件元数据主键',
    report_id BIGINT UNSIGNED NOT NULL COMMENT '所属报销单 ID',
    original_filename VARCHAR(255) NOT NULL COMMENT '用户上传时的原始文件名',
    storage_path VARCHAR(500) NOT NULL COMMENT '本地存储相对路径或对象 key',
    file_size BIGINT UNSIGNED NOT NULL COMMENT '文件大小，单位为字节',
    content_type VARCHAR(100) NULL COMMENT '文件 MIME 类型，例如 image/png',
    sha256 CHAR(64) NOT NULL COMMENT '文件内容 SHA-256，用于去重',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '上传时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_attachment_report_sha256 (report_id, sha256),
    KEY idx_attachment_report_created (report_id, created_at),
    CONSTRAINT fk_attachment_report
        FOREIGN KEY (report_id) REFERENCES expense_report (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='报销附件元数据';
