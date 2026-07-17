package com.penmate.backend.infrastructure.persistence.storybible;

import com.penmate.backend.domain.storybible.model.StoryBible;
import com.penmate.backend.domain.storybible.model.StoryBibleAlias;
import com.penmate.backend.domain.storybible.model.StoryBibleCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeItem;
import com.penmate.backend.domain.storybible.model.StoryBibleChangeset;
import com.penmate.backend.domain.storybible.model.StoryBibleNode;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeCategory;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeTag;
import com.penmate.backend.domain.storybible.model.StoryBibleNodeType;
import com.penmate.backend.domain.storybible.model.StoryBibleProgression;
import com.penmate.backend.domain.storybible.model.StoryBibleRelation;
import com.penmate.backend.domain.storybible.model.StoryBibleTag;
import com.penmate.backend.domain.storybible.model.StoryBibleViewPreference;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface StoryBibleMapper {

    @Select("""
            SELECT id, story_bible_id, project_id, title, description, content_revision,
                   created_at, updated_at, deleted_at
            FROM story_bibles
            WHERE project_id = #{projectId} AND deleted_at IS NULL
            LIMIT 1
            """)
    StoryBible findByProjectId(@Param("projectId") Long projectId);

    @Insert("""
            INSERT INTO story_bibles(story_bible_id, project_id, title, description, content_revision)
            VALUES(#{storyBibleId}, #{projectId}, #{title}, #{description}, #{contentRevision})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertStoryBible(StoryBible storyBible);

    @Update("""
            UPDATE story_bibles
            SET content_revision = content_revision + 1
            WHERE story_bible_id = #{storyBibleId}
              AND content_revision = #{expectedRevision}
              AND deleted_at IS NULL
            """)
    int incrementContentRevision(@Param("storyBibleId") Long storyBibleId,
                                 @Param("expectedRevision") Long expectedRevision);

    @Select("""
            SELECT id, type_id, story_bible_id, type_code, semantic_family, display_name, icon_code,
                   CAST(field_schema_json AS CHAR) AS field_schema_json, is_system AS system, sort_order,
                   created_at, updated_at, archived_at
            FROM story_bible_node_types
            WHERE (story_bible_id = #{storyBibleId} OR story_bible_id IS NULL)
              AND archived_at IS NULL
            ORDER BY semantic_family, sort_order, id
            """)
    List<StoryBibleNodeType> findNodeTypes(@Param("storyBibleId") Long storyBibleId);

    @Select("""
            SELECT id, type_id, story_bible_id, type_code, semantic_family, display_name, icon_code,
                   CAST(field_schema_json AS CHAR) AS field_schema_json, is_system AS system, sort_order,
                   created_at, updated_at, archived_at
            FROM story_bible_node_types
            WHERE type_id = #{typeId}
              AND (story_bible_id = #{storyBibleId} OR story_bible_id IS NULL)
              AND archived_at IS NULL
            LIMIT 1
            """)
    StoryBibleNodeType findNodeType(@Param("storyBibleId") Long storyBibleId, @Param("typeId") Long typeId);

    @Insert("""
            INSERT INTO story_bible_node_types(
                type_id, story_bible_id, type_code, semantic_family, display_name, icon_code,
                field_schema_json, is_system, sort_order
            ) VALUES (
                #{typeId}, #{storyBibleId}, #{typeCode}, #{semanticFamily}, #{displayName}, #{iconCode},
                #{fieldSchemaJson}, #{system}, #{sortOrder}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertNodeType(StoryBibleNodeType nodeType);

    @Update("""
            UPDATE story_bible_node_types
            SET display_name = #{displayName}, icon_code = #{iconCode}, field_schema_json = #{fieldSchemaJson},
                sort_order = #{sortOrder}
            WHERE story_bible_id = #{storyBibleId} AND type_id = #{typeId} AND archived_at IS NULL
            """)
    int updateNodeType(StoryBibleNodeType nodeType);

    @Update("""
            UPDATE story_bible_node_types SET archived_at = CURRENT_TIMESTAMP(3)
            WHERE story_bible_id = #{storyBibleId} AND type_id = #{typeId} AND archived_at IS NULL
            """)
    int archiveNodeType(@Param("storyBibleId") Long storyBibleId, @Param("typeId") Long typeId);

    @Select("""
            <script>
            SELECT id, node_id, story_bible_id, type_id, title, summary, body_markdown,
                   CAST(attributes_json AS CHAR) AS attributes_json, inclusion_policy, canon_status, revision,
                   created_by, updated_by, created_at, updated_at, archived_at, deleted_at
            FROM story_bible_nodes
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL
            <if test="typeId != null">AND type_id = #{typeId}</if>
            <if test="canonStatus != null and canonStatus != ''">AND canon_status = #{canonStatus}</if>
            <if test="query != null and query != ''">
              AND (title LIKE CONCAT('%', #{query}, '%')
                   OR summary LIKE CONCAT('%', #{query}, '%')
                   OR body_markdown LIKE CONCAT('%', #{query}, '%'))
            </if>
            ORDER BY updated_at DESC, id DESC
            </script>
            """)
    List<StoryBibleNode> findNodes(@Param("storyBibleId") Long storyBibleId,
                                   @Param("typeId") Long typeId,
                                   @Param("canonStatus") String canonStatus,
                                   @Param("query") String query);

    @Select("""
            <script>
            SELECT n.id, n.node_id, n.story_bible_id, n.type_id, n.title, n.summary, n.body_markdown,
                   CAST(n.attributes_json AS CHAR) AS attributes_json, n.inclusion_policy, n.canon_status, n.revision,
                   n.created_by, n.updated_by, n.created_at, n.updated_at, n.archived_at, n.deleted_at
            FROM story_bible_nodes n
            WHERE n.story_bible_id = #{storyBibleId} AND n.deleted_at IS NULL
            <if test="typeId != null">AND n.type_id = #{typeId}</if>
            <if test="canonStatus != null and canonStatus != ''">AND n.canon_status = #{canonStatus}</if>
            <if test="categoryId != null">
              AND EXISTS (
                SELECT 1 FROM story_bible_node_categories nc
                WHERE nc.story_bible_id = n.story_bible_id AND nc.node_id = n.node_id
                  AND nc.category_id = #{categoryId}
              )
            </if>
            <if test="tagId != null">
              AND EXISTS (
                SELECT 1 FROM story_bible_node_tags nt
                WHERE nt.story_bible_id = n.story_bible_id AND nt.node_id = n.node_id
                  AND nt.tag_id = #{tagId}
              )
            </if>
            <if test="query != null and query != ''">
              AND (
                n.title LIKE CONCAT('%', #{query}, '%')
                OR n.summary LIKE CONCAT('%', #{query}, '%')
                OR n.body_markdown LIKE CONCAT('%', #{query}, '%')
                OR EXISTS (
                  SELECT 1 FROM story_bible_aliases a
                  WHERE a.story_bible_id = n.story_bible_id AND a.node_id = n.node_id
                    AND a.deleted_at IS NULL AND a.alias LIKE CONCAT('%', #{query}, '%')
                )
              )
            </if>
            ORDER BY n.updated_at DESC, n.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<StoryBibleNode> findNodesFiltered(@Param("storyBibleId") Long storyBibleId,
                                           @Param("typeId") Long typeId,
                                           @Param("canonStatus") String canonStatus,
                                           @Param("query") String query,
                                           @Param("categoryId") Long categoryId,
                                           @Param("tagId") Long tagId,
                                           @Param("limit") int limit);

    @Select("""
            <script>
            SELECT id, node_id, story_bible_id, type_id, title, summary, body_markdown,
                   CAST(attributes_json AS CHAR) AS attributes_json, inclusion_policy, canon_status, revision,
                   created_by, updated_by, created_at, updated_at, archived_at, deleted_at
            FROM story_bible_nodes
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL AND canon_status = 'CANON'
              AND (
                <foreach collection="terms" item="term" separator=" OR ">
                  title LIKE CONCAT('%', #{term}, '%') OR summary LIKE CONCAT('%', #{term}, '%')
                  OR body_markdown LIKE CONCAT('%', #{term}, '%')
                </foreach>
              )
            ORDER BY inclusion_policy = 'ALWAYS_INCLUDE' DESC, updated_at DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<StoryBibleNode> searchNodesLexically(@Param("storyBibleId") Long storyBibleId,
                                              @Param("terms") List<String> terms,
                                              @Param("limit") int limit);

    @Select("""
            SELECT id, node_id, story_bible_id, type_id, title, summary, body_markdown,
                   CAST(attributes_json AS CHAR) AS attributes_json, inclusion_policy, canon_status, revision,
                   created_by, updated_by, created_at, updated_at, archived_at, deleted_at
            FROM story_bible_nodes
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL
              AND canon_status = 'CANON' AND inclusion_policy = 'ALWAYS_INCLUDE'
            ORDER BY id
            """)
    List<StoryBibleNode> findAlwaysIncludeNodes(Long storyBibleId);

    @Select("""
            <script>
            SELECT id, node_id, story_bible_id, type_id, title, summary, body_markdown,
                   CAST(attributes_json AS CHAR) AS attributes_json, inclusion_policy, canon_status, revision,
                   created_by, updated_by, created_at, updated_at, archived_at, deleted_at
            FROM story_bible_nodes
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL AND node_id IN
            <foreach collection="nodeIds" item="nodeId" open="(" separator="," close=")">#{nodeId}</foreach>
            ORDER BY id
            </script>
            """)
    List<StoryBibleNode> findNodesByIds(@Param("storyBibleId") Long storyBibleId,
                                        @Param("nodeIds") List<Long> nodeIds);

    @Select("""
            SELECT id, node_id, story_bible_id, type_id, title, summary, body_markdown,
                   CAST(attributes_json AS CHAR) AS attributes_json, inclusion_policy, canon_status, revision,
                   created_by, updated_by, created_at, updated_at, archived_at, deleted_at
            FROM story_bible_nodes
            WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId} AND deleted_at IS NULL
            LIMIT 1
            """)
    StoryBibleNode findNode(@Param("storyBibleId") Long storyBibleId, @Param("nodeId") Long nodeId);

    @Insert("""
            INSERT INTO story_bible_nodes(
                node_id, story_bible_id, type_id, title, summary, body_markdown, attributes_json,
                inclusion_policy, canon_status, revision, created_by, updated_by
            ) VALUES (
                #{nodeId}, #{storyBibleId}, #{typeId}, #{title}, #{summary}, #{bodyMarkdown}, #{attributesJson},
                #{inclusionPolicy}, #{canonStatus}, #{revision}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertNode(StoryBibleNode node);

    @Update("""
            UPDATE story_bible_nodes
            SET type_id = #{node.typeId}, title = #{node.title}, summary = #{node.summary},
                body_markdown = #{node.bodyMarkdown}, attributes_json = #{node.attributesJson},
                inclusion_policy = #{node.inclusionPolicy}, canon_status = #{node.canonStatus},
                revision = revision + 1, updated_by = #{node.updatedBy},
                archived_at = CASE WHEN #{node.canonStatus} = 'ARCHIVED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END
            WHERE story_bible_id = #{node.storyBibleId} AND node_id = #{node.nodeId}
              AND revision = #{expectedRevision} AND deleted_at IS NULL
            """)
    int updateNode(@Param("node") StoryBibleNode node, @Param("expectedRevision") Long expectedRevision);

    @Update("""
            UPDATE story_bible_nodes
            SET deleted_at = CURRENT_TIMESTAMP(3), revision = revision + 1, updated_by = #{updatedBy}
            WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId}
              AND revision = #{expectedRevision} AND deleted_at IS NULL
            """)
    int softDeleteNode(@Param("storyBibleId") Long storyBibleId,
                       @Param("nodeId") Long nodeId,
                       @Param("expectedRevision") Long expectedRevision,
                       @Param("updatedBy") Long updatedBy);

    @Select("""
            SELECT id, alias_id, story_bible_id, node_id, alias, normalized_alias, created_at, deleted_at
            FROM story_bible_aliases
            WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId} AND deleted_at IS NULL
            ORDER BY id
            """)
    List<StoryBibleAlias> findAliases(@Param("storyBibleId") Long storyBibleId, @Param("nodeId") Long nodeId);

    @Select("""
            <script>
            SELECT id, alias_id, story_bible_id, node_id, alias, normalized_alias, created_at, deleted_at
            FROM story_bible_aliases
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL AND node_id IN
              <foreach collection="nodeIds" item="nodeId" open="(" separator="," close=")">#{nodeId}</foreach>
            ORDER BY node_id, normalized_alias, id
            </script>
            """)
    List<StoryBibleAlias> findAliasesByNodeIds(@Param("storyBibleId") Long storyBibleId,
                                               @Param("nodeIds") List<Long> nodeIds);

    @Select("""
            SELECT id, alias_id, story_bible_id, node_id, alias, normalized_alias, created_at, deleted_at
            FROM story_bible_aliases
            WHERE story_bible_id = #{storyBibleId} AND normalized_alias = #{normalizedAlias} AND deleted_at IS NULL
            ORDER BY id
            """)
    List<StoryBibleAlias> findByNormalizedAlias(@Param("storyBibleId") Long storyBibleId,
                                                @Param("normalizedAlias") String normalizedAlias);

    @Insert("""
            INSERT INTO story_bible_aliases(alias_id, story_bible_id, node_id, alias, normalized_alias)
            VALUES(#{aliasId}, #{storyBibleId}, #{nodeId}, #{alias}, #{normalizedAlias}) AS incoming
            ON DUPLICATE KEY UPDATE alias = incoming.alias, deleted_at = NULL
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAlias(StoryBibleAlias alias);

    @Update("""
            UPDATE story_bible_aliases SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE story_bible_id = #{storyBibleId} AND alias_id = #{aliasId} AND deleted_at IS NULL
            """)
    int softDeleteAlias(@Param("storyBibleId") Long storyBibleId, @Param("aliasId") Long aliasId);

    @Select("""
            SELECT id, category_id, story_bible_id, parent_category_id, name, sort_order,
                   created_at, updated_at, deleted_at
            FROM story_bible_categories
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL
            ORDER BY parent_category_id, sort_order, id
            """)
    List<StoryBibleCategory> findCategories(@Param("storyBibleId") Long storyBibleId);

    @Insert("""
            INSERT INTO story_bible_categories(category_id, story_bible_id, parent_category_id, name, sort_order)
            VALUES(#{categoryId}, #{storyBibleId}, #{parentCategoryId}, #{name}, #{sortOrder})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCategory(StoryBibleCategory category);

    @Update("""
            UPDATE story_bible_categories
            SET parent_category_id = #{parentCategoryId}, name = #{name}, sort_order = #{sortOrder}
            WHERE story_bible_id = #{storyBibleId} AND category_id = #{categoryId} AND deleted_at IS NULL
            """)
    int updateCategory(StoryBibleCategory category);

    @Update("""
            UPDATE story_bible_categories SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE story_bible_id = #{storyBibleId} AND category_id = #{categoryId} AND deleted_at IS NULL
            """)
    int softDeleteCategory(@Param("storyBibleId") Long storyBibleId, @Param("categoryId") Long categoryId);

    @Insert("""
            INSERT INTO story_bible_node_categories(story_bible_id, node_id, category_id)
            VALUES(#{storyBibleId}, #{nodeId}, #{categoryId})
            """)
    int insertNodeCategory(StoryBibleNodeCategory membership);

    @Select("""
            SELECT id, story_bible_id, node_id, category_id, created_at
            FROM story_bible_node_categories
            WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId}
            ORDER BY id
            """)
    List<StoryBibleNodeCategory> findNodeCategories(@Param("storyBibleId") Long storyBibleId,
                                                    @Param("nodeId") Long nodeId);

    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM story_bible_node_categories WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId}
            """)
    int deleteNodeCategories(@Param("storyBibleId") Long storyBibleId, @Param("nodeId") Long nodeId);

    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM story_bible_node_categories
            WHERE story_bible_id = #{storyBibleId} AND category_id = #{categoryId}
            """)
    int deleteNodeCategoriesByCategory(@Param("storyBibleId") Long storyBibleId,
                                       @Param("categoryId") Long categoryId);

    @Select("""
            SELECT id, tag_id, story_bible_id, name, normalized_name, color, created_at, updated_at, deleted_at
            FROM story_bible_tags WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL ORDER BY name, id
            """)
    List<StoryBibleTag> findTags(@Param("storyBibleId") Long storyBibleId);

    @Insert("""
            INSERT INTO story_bible_tags(tag_id, story_bible_id, name, normalized_name, color)
            VALUES(#{tagId}, #{storyBibleId}, #{name}, #{normalizedName}, #{color})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertTag(StoryBibleTag tag);

    @Update("""
            UPDATE story_bible_tags SET name = #{name}, normalized_name = #{normalizedName}, color = #{color}
            WHERE story_bible_id = #{storyBibleId} AND tag_id = #{tagId} AND deleted_at IS NULL
            """)
    int updateTag(StoryBibleTag tag);

    @Update("""
            UPDATE story_bible_tags SET deleted_at = CURRENT_TIMESTAMP(3)
            WHERE story_bible_id = #{storyBibleId} AND tag_id = #{tagId} AND deleted_at IS NULL
            """)
    int softDeleteTag(@Param("storyBibleId") Long storyBibleId, @Param("tagId") Long tagId);

    @Insert("""
            INSERT INTO story_bible_node_tags(story_bible_id, node_id, tag_id)
            VALUES(#{storyBibleId}, #{nodeId}, #{tagId})
            """)
    int insertNodeTag(StoryBibleNodeTag membership);

    @Select("""
            SELECT id, story_bible_id, node_id, tag_id, created_at
            FROM story_bible_node_tags
            WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId}
            ORDER BY id
            """)
    List<StoryBibleNodeTag> findNodeTags(@Param("storyBibleId") Long storyBibleId,
                                         @Param("nodeId") Long nodeId);

    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM story_bible_node_tags WHERE story_bible_id = #{storyBibleId} AND node_id = #{nodeId}
            """)
    int deleteNodeTags(@Param("storyBibleId") Long storyBibleId, @Param("nodeId") Long nodeId);

    @org.apache.ibatis.annotations.Delete("""
            DELETE FROM story_bible_node_tags
            WHERE story_bible_id = #{storyBibleId} AND tag_id = #{tagId}
            """)
    int deleteNodeTagsByTag(@Param("storyBibleId") Long storyBibleId,
                            @Param("tagId") Long tagId);

    @Select("""
            <script>
            SELECT id, relation_id, story_bible_id, source_node_id, relation_type, target_node_id, description,
                   CAST(attributes_json AS CHAR) AS attributes_json, revision, created_by, updated_by,
                   created_at, updated_at, deleted_at
            FROM story_bible_relations
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL
            <if test="nodeIds != null and !nodeIds.isEmpty()">
              AND (source_node_id IN
                <foreach collection="nodeIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                OR target_node_id IN
                <foreach collection="nodeIds" item="id" open="(" separator="," close=")">#{id}</foreach>)
            </if>
            ORDER BY id
            </script>
            """)
    List<StoryBibleRelation> findRelations(@Param("storyBibleId") Long storyBibleId,
                                           @Param("nodeIds") List<Long> nodeIds);

    @Select("""
            SELECT id, relation_id, story_bible_id, source_node_id, relation_type, target_node_id, description,
                   CAST(attributes_json AS CHAR) AS attributes_json, revision, created_by, updated_by,
                   created_at, updated_at, deleted_at
            FROM story_bible_relations
            WHERE story_bible_id = #{storyBibleId} AND relation_id = #{relationId} AND deleted_at IS NULL
            LIMIT 1
            """)
    StoryBibleRelation findRelation(@Param("storyBibleId") Long storyBibleId, @Param("relationId") Long relationId);

    @Insert("""
            INSERT INTO story_bible_relations(
                relation_id, story_bible_id, source_node_id, relation_type, target_node_id, description,
                attributes_json, revision, created_by, updated_by
            ) VALUES (
                #{relationId}, #{storyBibleId}, #{sourceNodeId}, #{relationType}, #{targetNodeId}, #{description},
                #{attributesJson}, #{revision}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRelation(StoryBibleRelation relation);

    @Update("""
            UPDATE story_bible_relations
            SET relation_type = #{relation.relationType}, target_node_id = #{relation.targetNodeId},
                description = #{relation.description}, attributes_json = #{relation.attributesJson},
                revision = revision + 1, updated_by = #{relation.updatedBy}
            WHERE story_bible_id = #{relation.storyBibleId} AND relation_id = #{relation.relationId}
              AND revision = #{expectedRevision} AND deleted_at IS NULL
            """)
    int updateRelation(@Param("relation") StoryBibleRelation relation, @Param("expectedRevision") Long expectedRevision);

    @Update("""
            UPDATE story_bible_relations
            SET deleted_at = CURRENT_TIMESTAMP(3), revision = revision + 1, updated_by = #{updatedBy}
            WHERE story_bible_id = #{storyBibleId} AND relation_id = #{relationId}
              AND revision = #{expectedRevision} AND deleted_at IS NULL
            """)
    int softDeleteRelation(@Param("storyBibleId") Long storyBibleId,
                           @Param("relationId") Long relationId,
                           @Param("expectedRevision") Long expectedRevision,
                           @Param("updatedBy") Long updatedBy);

    @Select("""
            <script>
            SELECT id, progression_id, story_bible_id, node_id, anchor_chapter_id, end_chapter_id,
                   story_event_node_id, CAST(patch_json AS CHAR) AS patch_json, summary, revision,
                   created_by, updated_by, created_at, updated_at, deleted_at
            FROM story_bible_progressions
            WHERE story_bible_id = #{storyBibleId} AND deleted_at IS NULL
            <if test="nodeIds != null and !nodeIds.isEmpty()">
              AND node_id IN
                <foreach collection="nodeIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </if>
            ORDER BY id
            </script>
            """)
    List<StoryBibleProgression> findProgressions(@Param("storyBibleId") Long storyBibleId,
                                                 @Param("nodeIds") List<Long> nodeIds);

    @Select("""
            SELECT id, progression_id, story_bible_id, node_id, anchor_chapter_id, end_chapter_id,
                   story_event_node_id, CAST(patch_json AS CHAR) AS patch_json, summary, revision,
                   created_by, updated_by, created_at, updated_at, deleted_at
            FROM story_bible_progressions
            WHERE story_bible_id = #{storyBibleId} AND progression_id = #{progressionId} AND deleted_at IS NULL
            LIMIT 1
            """)
    StoryBibleProgression findProgression(@Param("storyBibleId") Long storyBibleId,
                                          @Param("progressionId") Long progressionId);

    @Insert("""
            INSERT INTO story_bible_progressions(
                progression_id, story_bible_id, node_id, anchor_chapter_id, end_chapter_id,
                story_event_node_id, patch_json, summary, revision, created_by, updated_by
            ) VALUES (
                #{progressionId}, #{storyBibleId}, #{nodeId}, #{anchorChapterId}, #{endChapterId},
                #{storyEventNodeId}, #{patchJson}, #{summary}, #{revision}, #{createdBy}, #{updatedBy}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProgression(StoryBibleProgression progression);

    @Update("""
            UPDATE story_bible_progressions
            SET anchor_chapter_id = #{progression.anchorChapterId}, end_chapter_id = #{progression.endChapterId},
                story_event_node_id = #{progression.storyEventNodeId}, patch_json = #{progression.patchJson},
                summary = #{progression.summary}, revision = revision + 1, updated_by = #{progression.updatedBy}
            WHERE story_bible_id = #{progression.storyBibleId} AND progression_id = #{progression.progressionId}
              AND revision = #{expectedRevision} AND deleted_at IS NULL
            """)
    int updateProgression(@Param("progression") StoryBibleProgression progression,
                          @Param("expectedRevision") Long expectedRevision);

    @Update("""
            UPDATE story_bible_progressions
            SET deleted_at = CURRENT_TIMESTAMP(3), revision = revision + 1, updated_by = #{updatedBy}
            WHERE story_bible_id = #{storyBibleId} AND progression_id = #{progressionId}
              AND revision = #{expectedRevision} AND deleted_at IS NULL
            """)
    int softDeleteProgression(@Param("storyBibleId") Long storyBibleId,
                              @Param("progressionId") Long progressionId,
                              @Param("expectedRevision") Long expectedRevision,
                              @Param("updatedBy") Long updatedBy);

    @Select("""
            SELECT id, story_bible_id, view_code, display_name, hidden, sort_order, updated_by, updated_at
            FROM story_bible_view_preferences WHERE story_bible_id = #{storyBibleId} ORDER BY sort_order, id
            """)
    List<StoryBibleViewPreference> findViewPreferences(@Param("storyBibleId") Long storyBibleId);

    @Insert("""
            INSERT INTO story_bible_view_preferences(
                story_bible_id, view_code, display_name, hidden, sort_order, updated_by
            ) VALUES (
                #{storyBibleId}, #{viewCode}, #{displayName}, #{hidden}, #{sortOrder}, #{updatedBy}
            ) AS new
            ON DUPLICATE KEY UPDATE display_name = new.display_name, hidden = new.hidden,
                                    sort_order = new.sort_order, updated_by = new.updated_by
            """)
    int upsertViewPreference(StoryBibleViewPreference preference);

    @Insert("""
            INSERT INTO story_bible_changesets(
                changeset_id, story_bible_id, content_revision, actor_type, actor_id, source_run_id, change_summary
            ) VALUES (
                #{changesetId}, #{storyBibleId}, #{contentRevision}, #{actorType}, #{actorId}, #{sourceRunId}, #{changeSummary}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChangeset(StoryBibleChangeset changeset);

    @Insert("""
            INSERT INTO story_bible_change_items(
                change_item_id, changeset_id, entity_type, entity_id, operation,
                field_path, before_json, after_json
            ) VALUES (
                #{changeItemId}, #{changesetId}, #{entityType}, #{entityId}, #{operation},
                #{fieldPath}, #{beforeJson}, #{afterJson}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertChangeItem(StoryBibleChangeItem item);

    @Select("""
            SELECT id, changeset_id, story_bible_id, content_revision, actor_type, actor_id,
                   source_run_id, change_summary, created_at
            FROM story_bible_changesets
            WHERE story_bible_id = #{storyBibleId}
            ORDER BY created_at DESC, id DESC LIMIT #{limit}
            """)
    List<StoryBibleChangeset> findRecentChangesets(@Param("storyBibleId") Long storyBibleId,
                                                   @Param("limit") int limit);

    @Select("""
            SELECT id, changeset_id, story_bible_id, content_revision, actor_type, actor_id,
                   source_run_id, change_summary, created_at
            FROM story_bible_changesets
            WHERE story_bible_id = #{storyBibleId} AND changeset_id = #{changesetId}
            LIMIT 1
            """)
    StoryBibleChangeset findChangeset(@Param("storyBibleId") Long storyBibleId,
                                      @Param("changesetId") Long changesetId);

    @Select("""
            SELECT DISTINCT sc.id, sc.changeset_id, sc.story_bible_id, sc.content_revision,
                   sc.actor_type, sc.actor_id, sc.source_run_id, sc.change_summary, sc.created_at
            FROM story_bible_changesets sc
            JOIN story_bible_change_items ci ON ci.changeset_id = sc.changeset_id
            LEFT JOIN story_bible_relations r
              ON ci.entity_type = 'RELATION' AND ci.entity_id = r.relation_id
             AND r.story_bible_id = sc.story_bible_id
            LEFT JOIN story_bible_progressions p
              ON ci.entity_type = 'PROGRESSION' AND ci.entity_id = p.progression_id
             AND p.story_bible_id = sc.story_bible_id
            WHERE sc.story_bible_id = #{storyBibleId}
              AND (
                (ci.entity_type = 'NODE' AND ci.entity_id = #{nodeId})
                OR (ci.entity_type = 'RELATION' AND (r.source_node_id = #{nodeId} OR r.target_node_id = #{nodeId}))
                OR (ci.entity_type = 'PROGRESSION' AND p.node_id = #{nodeId})
              )
            ORDER BY sc.created_at DESC, sc.id DESC
            LIMIT #{limit}
            """)
    List<StoryBibleChangeset> findChangesetsForNode(@Param("storyBibleId") Long storyBibleId,
                                                    @Param("nodeId") Long nodeId,
                                                    @Param("limit") int limit);

    @Select("""
            SELECT id, changeset_id, story_bible_id, content_revision, actor_type, actor_id,
                   source_run_id, change_summary, created_at
            FROM story_bible_changesets
            WHERE story_bible_id = #{storyBibleId} AND created_at &lt; #{cutoff}
              AND changeset_id NOT IN (
                  SELECT changeset_id FROM story_bible_changesets
                  WHERE story_bible_id = #{storyBibleId}
                  ORDER BY created_at DESC, id DESC LIMIT #{retainCount}
              )
            ORDER BY created_at, id
            """)
    List<StoryBibleChangeset> findChangesetsBefore(@Param("storyBibleId") Long storyBibleId,
                                                   @Param("cutoff") LocalDateTime cutoff,
                                                   @Param("retainCount") int retainCount);

    @Select("""
            SELECT DISTINCT sb.id, sb.story_bible_id, sb.project_id, sb.title, sb.description,
                            sb.content_revision, sb.created_at, sb.updated_at, sb.deleted_at
            FROM story_bibles sb
            JOIN story_bible_changesets sc ON sc.story_bible_id = sb.story_bible_id
            WHERE sb.deleted_at IS NULL AND sc.created_at &lt; #{cutoff}
            ORDER BY sb.id
            """)
    List<StoryBible> findStoryBiblesWithChangesetsBefore(@Param("cutoff") LocalDateTime cutoff);

    @Select("""
            <script>
            SELECT id, change_item_id, changeset_id, entity_type, entity_id, operation,
                   field_path, CAST(before_json AS CHAR) AS before_json,
                   CAST(after_json AS CHAR) AS after_json, created_at
            FROM story_bible_change_items
            WHERE changeset_id IN
              <foreach collection="changesetIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            ORDER BY changeset_id, id
            </script>
            """)
    List<StoryBibleChangeItem> findChangeItemsByChangesetIds(@Param("changesetIds") List<Long> changesetIds);

    @Delete("""
            <script>
            DELETE FROM story_bible_change_items WHERE changeset_id IN
              <foreach collection="changesetIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteChangeItemsByChangesetIds(@Param("changesetIds") List<Long> changesetIds);

    @Delete("""
            <script>
            DELETE FROM story_bible_changesets
            WHERE story_bible_id = #{storyBibleId} AND changeset_id IN
              <foreach collection="changesetIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteChangesetsByIds(@Param("storyBibleId") Long storyBibleId,
                              @Param("changesetIds") List<Long> changesetIds);
}
