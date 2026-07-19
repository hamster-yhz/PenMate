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
    origin_etag, mime_type, parse_status, index_status)
VALUES (
    920911, 920001, 'story_bible', 'Ashen City facts', 'story-bible://920401',
    'demo/920001/rag/920911.md', 'etag-demo-920911', 'text/markdown', 'done', 'indexed')
ON CONFLICT (document_id) DO UPDATE SET
    title = EXCLUDED.title,
    parse_status = EXCLUDED.parse_status,
    index_status = EXCLUDED.index_status,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO rag_chunks(
    chunk_id, project_id, document_id, chunk_no, content_text, token_count,
    vector_id, vector_store, embedding_provider, embedding_model, embedding_dim,
    embedding_version, metadata_json)
VALUES (
    920921, 920001, 920911, 1, 'Lin Jin carries a forbidden map.', 8,
    'demo-920921', 'milvus', 'test', 'test-embedding', 8, 'v1',
    '{"storyBibleNodeId":920601}'::jsonb)
ON CONFLICT (chunk_id) DO UPDATE SET
    content_text = EXCLUDED.content_text,
    metadata_json = EXCLUDED.metadata_json;
