CREATE TABLE IF NOT EXISTS storage_objects (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    storage_object_id BIGINT UNSIGNED NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    region VARCHAR(64) NULL,
    etag VARCHAR(128) NULL,
    size BIGINT UNSIGNED NULL,
    storage_class VARCHAR(32) NULL,
    ref_type VARCHAR(50) NOT NULL,
    ref_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_storage_objects_storage_object_id (storage_object_id),
    UNIQUE KEY uk_storage_object_key (object_key),
    KEY idx_storage_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_documents (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    document_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    doc_type VARCHAR(32) NOT NULL,
    title VARCHAR(200) NOT NULL,
    source_ref VARCHAR(500) NULL,
    origin_object_key VARCHAR(500) NOT NULL,
    origin_etag VARCHAR(128) NULL,
    mime_type VARCHAR(100) NULL,
    parse_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    index_status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    UNIQUE KEY uk_rag_documents_document_id (document_id),
    KEY idx_rag_project_status (project_id, parse_status, index_status, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_chunks (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    chunk_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    document_id BIGINT UNSIGNED NOT NULL,
    chunk_no INT UNSIGNED NOT NULL,
    content_text MEDIUMTEXT NOT NULL,
    token_count INT UNSIGNED NOT NULL,
    vector_id VARCHAR(128) NOT NULL,
    vector_store VARCHAR(32) NOT NULL,
    embedding_provider VARCHAR(64) NOT NULL,
    embedding_model VARCHAR(64) NOT NULL,
    embedding_dim INT UNSIGNED NOT NULL,
    embedding_version VARCHAR(32) NOT NULL,
    metadata_json JSON NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_chunks_chunk_id (chunk_id),
    UNIQUE KEY uk_chunks_vector (vector_id, vector_store),
    KEY idx_chunks_doc_no (document_id, chunk_no),
    KEY idx_chunks_project_doc (project_id, document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rag_retrieval_logs (
    id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
    retrieval_log_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    run_id BIGINT UNSIGNED NULL,
    query_text VARCHAR(500) NULL,
    hit_count INT NOT NULL DEFAULT 0,
    sources_json JSON NULL,
    latency_ms INT NULL,
    adopted TINYINT(1) NOT NULL DEFAULT 0,
    trace_id VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_rag_retrieval_logs_retrieval_log_id (retrieval_log_id),
    KEY idx_rag_retrieval_project_created (project_id, created_at),
    KEY idx_rag_retrieval_run_created (run_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

