-- RAG and object-storage records associated with the demo book.
INSERT INTO storage_objects(
    storage_object_id, object_key, bucket, provider, region, etag, size,
    storage_class, ref_type, ref_id)
VALUES (
    920901, 'demo/920001/chapters/920301.md', 'penmate-test', 's3', 'local',
    'etag-demo-920301', 4096, 'STANDARD', 'novel_chapter', 920301)
ON CONFLICT (storage_object_id) DO UPDATE SET etag = EXCLUDED.etag, size = EXCLUDED.size;

INSERT INTO rag_documents(
    document_id, project_id, doc_type, title, source_ref, origin_object_key,
    origin_etag, origin_checksum, origin_size, file_extension, mime_type,
    source_revision, parse_status, index_status)
VALUES (
    920911, 920001, 'KNOWLEDGE_DOCUMENT', 'Ashen City facts', 'document://920911',
    'demo/920001/rag/920911.md', 'etag-demo-920911',
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 256, 'md', 'text/markdown',
    1, 'PARSED', 'INDEXED')
ON CONFLICT (document_id) DO UPDATE SET
    title = EXCLUDED.title,
    parse_status = EXCLUDED.parse_status,
    index_status = EXCLUDED.index_status,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO rag_embedding_spaces(
    embedding_space_id, identity_hash, provider_id, protocol_code, normalized_base_url,
    model_name, embedding_dimension, distance_metric, storage_type, partition_name, space_status)
VALUES (
    920912, 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    1, 'OPENAI_EMBEDDINGS', 'https://example.test/v1', 'test-embedding', 8,
    'COSINE', 'VECTOR', 'rag_v_920912', 'ACTIVE')
ON CONFLICT (embedding_space_id) DO NOTHING;

INSERT INTO rag_index_builds(
    index_build_id, project_id, model_config_id, embedding_space_id, build_status,
    source_count, completed_source_count, chunk_count, embedded_chunk_count)
VALUES (920913, 920001, 920010, 920912, 'ACTIVE', 1, 1, 1, 0)
ON CONFLICT (index_build_id) DO NOTHING;

INSERT INTO rag_index_sources(
    source_index_id, index_build_id, project_id, source_type, source_id, source_revision,
    source_title, source_status, content_checksum, character_count, chunk_count, active, activated_at)
VALUES (
    920914, 920913, 920001, 'KNOWLEDGE_DOCUMENT', 920911, '1', 'Ashen City facts',
    'ACTIVE', 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
    35, 1, TRUE, CURRENT_TIMESTAMP(3))
ON CONFLICT (source_index_id) DO NOTHING;

INSERT INTO rag_chunks(
    chunk_id, source_index_id, index_build_id, project_id, embedding_space_id,
    source_type, source_id, chunk_no, content_text, character_count, token_count,
    content_hash, metadata_json)
VALUES (
    920921, 920914, 920913, 920001, 920912,
    'KNOWLEDGE_DOCUMENT', 920911, 1, 'Lin Jin carries a forbidden map.', 35, 8,
    'dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd',
    '{"storyBibleNodeId":920601}'::jsonb)
ON CONFLICT (chunk_id) DO UPDATE SET
    content_text = EXCLUDED.content_text,
    metadata_json = EXCLUDED.metadata_json;
