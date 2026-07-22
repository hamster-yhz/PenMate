package com.penmate.backend.infrastructure.persistence.rag;

import com.penmate.backend.domain.rag.model.ProjectAiConfiguration;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProjectAiConfigurationMapper {

    String COLUMNS = """
            project_ai_config_id, project_id, creative_model_config_id, embedding_model_config_id,
            story_bible_routing_mode, router_model_config_id,
            chunk_target_characters, chunk_overlap_characters, chunk_max_characters,
            retrieval_candidates, retrieval_top_k, retrieval_max_per_source, hnsw_ef_search,
            similarity_threshold, index_status, active_index_build_id,
            last_error_code, last_error_message, created_at, updated_at
            """;

    @Select("SELECT " + COLUMNS + " FROM project_ai_configurations WHERE project_id = #{projectId}")
    ProjectAiConfiguration findByProjectId(Long projectId);

    @Select("SELECT " + COLUMNS + " FROM project_ai_configurations WHERE project_id = #{projectId} FOR UPDATE")
    ProjectAiConfiguration findByProjectIdForUpdate(Long projectId);

    @Insert("""
            INSERT INTO project_ai_configurations(
                project_ai_config_id, project_id, creative_model_config_id, embedding_model_config_id,
                story_bible_routing_mode, router_model_config_id,
                chunk_target_characters, chunk_overlap_characters, chunk_max_characters,
                retrieval_candidates, retrieval_top_k, retrieval_max_per_source, hnsw_ef_search,
                similarity_threshold, index_status, active_index_build_id,
                last_error_code, last_error_message
            ) VALUES (
                #{projectAiConfigId}, #{projectId}, #{creativeModelConfigId}, #{embeddingModelConfigId},
                #{storyBibleRoutingMode}, #{routerModelConfigId},
                #{chunkTargetCharacters}, #{chunkOverlapCharacters}, #{chunkMaxCharacters},
                #{retrievalCandidates}, #{retrievalTopK}, #{retrievalMaxPerSource}, #{hnswEfSearch},
                #{similarityThreshold}, #{indexStatus}, #{activeIndexBuildId},
                #{lastErrorCode}, #{lastErrorMessage}
            )
            """)
    int insert(ProjectAiConfiguration configuration);

    @Update("""
            UPDATE project_ai_configurations
            SET creative_model_config_id = #{creativeModelConfigId},
                embedding_model_config_id = #{embeddingModelConfigId},
                story_bible_routing_mode = #{storyBibleRoutingMode},
                router_model_config_id = #{routerModelConfigId},
                chunk_target_characters = #{chunkTargetCharacters},
                chunk_overlap_characters = #{chunkOverlapCharacters},
                chunk_max_characters = #{chunkMaxCharacters},
                retrieval_candidates = #{retrievalCandidates},
                retrieval_top_k = #{retrievalTopK},
                retrieval_max_per_source = #{retrievalMaxPerSource},
                hnsw_ef_search = #{hnswEfSearch},
                similarity_threshold = #{similarityThreshold},
                index_status = #{indexStatus},
                active_index_build_id = #{activeIndexBuildId},
                last_error_code = #{lastErrorCode},
                last_error_message = #{lastErrorMessage},
                updated_at = CURRENT_TIMESTAMP(3)
            WHERE project_id = #{projectId}
            """)
    int update(ProjectAiConfiguration configuration);

}
