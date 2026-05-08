package com.penmate.backend.interfaces.api.novel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.penmate.backend.application.novel.NovelApplicationService;
import com.penmate.backend.domain.novel.model.NovelCard;
import com.penmate.backend.domain.novel.model.NovelChapter;
import com.penmate.backend.domain.novel.model.NovelChapterVersion;
import com.penmate.backend.domain.novel.model.NovelOutlineNode;
import com.penmate.backend.domain.novel.model.NovelProject;
import com.penmate.backend.interfaces.api.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class NovelControllerTest {

    @Mock
    private NovelApplicationService novelApplicationService;

    @InjectMocks
    private NovelController novelController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.standaloneSetup(novelController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    // 项目创建成功。
    void UT_NOVEL_PROJECT_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-CREATE";
        NovelProject project = new NovelProject();
        project.setId(10001L);
        project.setProjectId(10001L);
        project.setOwnerUserId(1001L);
        project.setTitle("第七星环");
        when(novelApplicationService.createProject(any(), eq(traceId))).thenReturn(project);

        mockMvc().perform(post("/api/v1/novels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerUserId", "1001",
                                "title", "第七星环",
                                "summary", "赛博东方"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").isString())
                .andExpect(jsonPath("$.data.ownerUserId").isString())
                .andExpect(jsonPath("$.data.projectId").value("10001"))
                .andExpect(jsonPath("$.data.ownerUserId").value("1001"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 项目创建参数错误。
    void UT_NOVEL_PROJECT_CREATE_INVALID_PARAM() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-CREATE-INVALID";

        mockMvc().perform(post("/api/v1/novels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "ownerUserId", "1001",
                                "title", ""
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    // 成员添加冲突（重复成员）。
    void UT_NOVEL_MEMBER_ADD_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-NOVEL-MEMBER-ADD-CONFLICT";
        doThrow(new IllegalArgumentException("Member already exists"))
                .when(novelApplicationService).addMember(eq(10001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/members")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "2001",
                                "memberRole", "editor"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    // 章节发布状态不允许。
    void UT_NOVEL_CHAPTER_PUBLISH_INVALID_STATE() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CHAPTER-PUBLISH-INVALID";
        doThrow(new IllegalArgumentException("Chapter status invalid for publish"))
                .when(novelApplicationService).publishChapter(10001L, 3001L, 1001L, traceId);

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/publish")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422))
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    // 版本恢复成功。
    void UT_NOVEL_VERSION_RESTORE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VERSION-RESTORE";
        NovelChapter chapter = new NovelChapter();
        chapter.setId(3001L);
        chapter.setChapterId(3001L);
        when(novelApplicationService.restoreChapterVersion(10001L, 3001L, 3, 1001L, traceId)).thenReturn(chapter);

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/versions/3/restore")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterId").isString())
                .andExpect(jsonPath("$.data.chapterId").value("3001"));
    }

    @Test
    // 正文 commit 缺少 objectKey 应在接口层返回参数校验错误，而不是 500。
    void UT_NOVEL_CONTENT_COMMIT_NULL_OBJECT_KEY_BAD_REQUEST() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CONTENT-COMMIT-NULL-OBJECT-KEY";

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/content-commit")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content("{" +
                                "\"objectKey\":null," +
                                "\"etag\":\"etag-1\"" +
                                "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("objectKey"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 正文 commit etag 校验失败。
    void UT_NOVEL_CONTENT_COMMIT_ETAG_MISMATCH() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CONTENT-COMMIT-ETAG";
        doThrow(new IllegalArgumentException("etag mismatch"))
                .when(novelApplicationService).commitChapterContent(eq(10001L), eq(3001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/content-commit")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "objectKey", "novels/10001/chapters/3001.md",
                                "etag", "bad-etag"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    // 正文 commit 空值空指针不应伪装成 422 业务异常。
    void UT_NOVEL_CONTENT_COMMIT_NULL_POINTER_SHOULD_RETURN_INTERNAL_SERVER_ERROR() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CONTENT-COMMIT-NULL-POINTER";
        doThrow(new NullPointerException("projectId must not be null"))
                .when(novelApplicationService).commitChapterContent(eq(10001L), eq(3001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/content-commit")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "objectKey", "novels/10001/chapters/3001.md",
                                "etag", "etag-1"
                        ))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data.status").value(500))
                .andExpect(jsonPath("$.data.errorCode").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data.message").value("系统开小差了，请稍后重试"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 大纲节点移动非法父节点。
    void UT_NOVEL_OUTLINE_MOVE_INVALID_PARENT() throws Exception {
        String traceId = "UT-TRACE-NOVEL-OUTLINE-MOVE-INVALID-PARENT";
        doThrow(new IllegalArgumentException("Invalid parent node"))
                .when(novelApplicationService).moveOutlineNode(eq(10001L), eq(9001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(patch("/api/v1/novels/10001/outlines/nodes/9001/move")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", "9999",
                                "sortOrder", 1
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422));
    }

    @Test
    // 设定卡类型错误。
    void UT_NOVEL_CARD_TYPE_INVALID() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CARD-TYPE-INVALID";
        doThrow(new IllegalArgumentException("Invalid card type"))
                .when(novelApplicationService).createCard(eq(10001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/cards")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardType", "unknown_type",
                                "name", "设定A"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.errorCode").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    // 关系图谱重复关系冲突。
    void UT_NOVEL_RELATION_DUPLICATED_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-NOVEL-RELATION-DUPLICATE";
        doThrow(new IllegalArgumentException("Relation already exists"))
                .when(novelApplicationService).createCardRelation(eq(10001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/card-relations")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fromCardId", "1",
                                "toCardId", "2",
                                "relationType", "ally"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422));
    }

    @Test
    // 章节版本创建成功。
    void UT_NOVEL_VERSION_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VERSION-CREATE";
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(2);
        when(novelApplicationService.createChapterVersion(eq(10001L), eq(3001L), any(), eq(traceId))).thenReturn(version);

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "changeType", "manual",
                                "changeReason", "润色",
                                "createdBy", "1001"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));
    }

    @Test
    // 项目更新成功。
    void UT_NOVEL_PROJECT_UPDATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-UPDATE";
        NovelProject project = new NovelProject();
        project.setId(10001L);
        project.setProjectId(10001L);
        project.setTitle("第七星环-修订");
        when(novelApplicationService.updateProject(eq(10001L), any(), eq(traceId))).thenReturn(project);

        mockMvc().perform(put("/api/v1/novels/10001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "第七星环-修订",
                                "summary", "赛博东方2",
                                "status", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").isString())
                .andExpect(jsonPath("$.data.projectId").value("10001"));
    }

    @Test
    // 项目删除后软删除可见性。
    void UT_NOVEL_PROJECT_DELETE_SOFT_DELETE_VISIBILITY() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-DELETE";
        doNothing().when(novelApplicationService).deleteProject(10001L, 1001L, traceId);

        mockMvc().perform(delete("/api/v1/novels/10001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"));
    }

    @Test
    // 删除项目缺少 operatorId 参数：应返回参数校验错误。
    void UT_NOVEL_PROJECT_DELETE_MISSING_OPERATOR_ID_BAD_REQUEST() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-DELETE-MISSING-OPERATOR-ID";

        mockMvc().perform(delete("/api/v1/novels/10001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.status").value(400))
                .andExpect(jsonPath("$.data.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.data.details[0].field").value("operatorId"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 成员添加成功。
    void UT_NOVEL_MEMBER_ADD_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-MEMBER-ADD";
        com.penmate.backend.domain.novel.model.NovelMember m = new com.penmate.backend.domain.novel.model.NovelMember();
        m.setProjectId(10001L);
        m.setUserId(2001L);
        m.setMemberRole("editor");
        when(novelApplicationService.addMember(eq(10001L), any(), eq(1001L), eq(traceId))).thenReturn(m);

        mockMvc().perform(post("/api/v1/novels/10001/members")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "userId", "2001",
                                "memberRole", "editor"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").isString())
                .andExpect(jsonPath("$.data.userId").isString())
                .andExpect(jsonPath("$.data.projectId").value("10001"))
                .andExpect(jsonPath("$.data.userId").value("2001"));
    }

    @Test
    // 成员更新成功。
    void UT_NOVEL_MEMBER_UPDATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-MEMBER-UPDATE";
        com.penmate.backend.domain.novel.model.NovelMember m = new com.penmate.backend.domain.novel.model.NovelMember();
        m.setProjectId(10001L);
        m.setUserId(2001L);
        m.setMemberRole("owner");
        when(novelApplicationService.updateMember(eq(10001L), eq(2001L), any(), eq(1001L), eq(traceId))).thenReturn(m);

        mockMvc().perform(patch("/api/v1/novels/10001/members/2001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of("memberRole", "owner"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberRole").value("owner"));
    }

    @Test
    // 卷创建成功。
    void UT_NOVEL_VOLUME_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VOLUME-CREATE";
        com.penmate.backend.domain.novel.model.NovelVolume volume = new com.penmate.backend.domain.novel.model.NovelVolume();
        volume.setId(2101L);
        volume.setVolumeId(2101L);
        volume.setProjectId(10001L);
        volume.setTitle("第一卷");
        when(novelApplicationService.createVolume(eq(10001L), any(), eq(1001L), eq(traceId))).thenReturn(volume);

        mockMvc().perform(post("/api/v1/novels/10001/volumes")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "第一卷",
                                "sortOrder", 1,
                                "description", "卷描述"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.volumeId").isString())
                .andExpect(jsonPath("$.data.volumeId").value("2101"));
    }

    @Test
    // 卷排序冲突。
    void UT_NOVEL_VOLUME_SORT_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VOLUME-SORT-CONFLICT";
        doThrow(new IllegalArgumentException("Volume sort order conflict"))
                .when(novelApplicationService).createVolume(eq(10001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/volumes")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "第一卷",
                                "sortOrder", 1,
                                "description", "卷描述"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422));
    }

    @Test
    // 章节发布成功。
    void UT_NOVEL_CHAPTER_PUBLISH_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CHAPTER-PUBLISH";
        doNothing().when(novelApplicationService).publishChapter(10001L, 3001L, 1001L, traceId);

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/publish")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("published"));
    }

    @Test
    // 正文上传URL获取成功。
    void UT_NOVEL_CONTENT_UPLOAD_URL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CONTENT-UPLOAD-URL";
        when(novelApplicationService.getChapterContentUploadUrl(10001L, 3001L)).thenReturn(Map.of(
                "uploadUrl", "https://oss/upload",
                "objectKey", "novels/10001/chapters/3001.md"
        ));

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/content-upload-url")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploadUrl").exists())
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 大纲节点移动成功。
    void UT_NOVEL_OUTLINE_MOVE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-OUTLINE-MOVE";
        doNothing().when(novelApplicationService).moveOutlineNode(eq(10001L), eq(9001L), any(), eq(1001L), eq(traceId));

        mockMvc().perform(patch("/api/v1/novels/10001/outlines/nodes/9001/move")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "parentId", "9000",
                                "sortOrder", 1
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("moved"));
    }

    @Test
    // 设定卡创建成功。
    void UT_NOVEL_CARD_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CARD-CREATE";
        NovelCard card = new NovelCard();
        card.setId(5001L);
        card.setCardId(5001L);
        card.setProjectId(10001L);
        card.setName("主角");
        when(novelApplicationService.createCard(eq(10001L), any(), eq(1001L), eq(traceId))).thenReturn(card);

        mockMvc().perform(post("/api/v1/novels/10001/cards")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardType", "character",
                                "name", "主角",
                                "summary", "简介"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardId").isString())
                .andExpect(jsonPath("$.data.cardId").value("5001"));
    }

    @Test
    // 关系图谱创建成功。
    void UT_NOVEL_RELATION_CREATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-RELATION-CREATE";
        com.penmate.backend.domain.novel.model.NovelCardRelation relation = new com.penmate.backend.domain.novel.model.NovelCardRelation();
        relation.setId(6001L);
        relation.setCardRelationId(6001L);
        relation.setProjectId(10001L);
        when(novelApplicationService.createCardRelation(eq(10001L), any(), eq(1001L), eq(traceId))).thenReturn(relation);

        mockMvc().perform(post("/api/v1/novels/10001/card-relations")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fromCardId", "1",
                                "toCardId", "2",
                                "relationType", "ally"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relationId").isString())
                .andExpect(jsonPath("$.data.relationId").value("6001"));
    }

    @Test
    // 关系图谱删除幂等。
    void UT_NOVEL_RELATION_DELETE_IDEMPOTENT() throws Exception {
        String traceId = "UT-TRACE-NOVEL-RELATION-DELETE-IDEMPOTENT";
        doNothing().when(novelApplicationService).deleteCardRelation(10001L, 6001L, 1001L, traceId);

        mockMvc().perform(delete("/api/v1/novels/10001/card-relations/6001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"));
    }

    @Test
    // 版本快照URL读取成功。
    void UT_NOVEL_SNAPSHOT_URL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-SNAPSHOT-URL";
        when(novelApplicationService.getChapterVersionSnapshotUrl(10001L, 3001L, 3))
                .thenReturn(Map.of("snapshotUrl", "https://oss/snapshot"));

        mockMvc().perform(get("/api/v1/novels/10001/chapters/3001/versions/3/snapshot-url")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshotUrl").exists())
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 版本号唯一冲突。
    void UT_NOVEL_VERSION_UNIQUE_CONFLICT() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VERSION-UNIQUE-CONFLICT";
        doThrow(new IllegalArgumentException("Version already exists"))
                .when(novelApplicationService).createChapterVersion(eq(10001L), eq(3001L), any(), eq(traceId));

        mockMvc().perform(post("/api/v1/novels/10001/chapters/3001/versions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "changeType", "manual",
                                "changeReason", "重复版本",
                                "createdBy", "1001"
                        ))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.data.status").value(422));
    }

    @Test
    // 项目列表查询成功。
    void UT_NOVEL_PROJECT_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-LIST";
        NovelProject project = new NovelProject();
        project.setId(10001L);
        project.setProjectId(10001L);
        project.setTitle("第七星环");
        when(novelApplicationService.listProjects()).thenReturn(java.util.List.of(project));

        mockMvc().perform(get("/api/v1/novels")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].projectId").isString())
                .andExpect(jsonPath("$.data[0].projectId").value("10001"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 项目详情查询成功。
    void UT_NOVEL_PROJECT_GET_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-PROJECT-GET";
        NovelProject project = new NovelProject();
        project.setId(10001L);
        project.setProjectId(10001L);
        project.setTitle("第七星环");
        when(novelApplicationService.getProject(10001L)).thenReturn(project);

        mockMvc().perform(get("/api/v1/novels/10001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").isString())
                .andExpect(jsonPath("$.data.projectId").value("10001"))
                .andExpect(jsonPath("$.data.title").value("第七星环"));
    }

    @Test
    // 成员移除成功。
    void UT_NOVEL_MEMBER_REMOVE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-MEMBER-REMOVE";
        doNothing().when(novelApplicationService).removeMember(10001L, 2001L, 1001L, traceId);

        mockMvc().perform(delete("/api/v1/novels/10001/members/2001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("removed"));
    }

    @Test
    // 章节版本列表查询成功。
    void UT_NOVEL_VERSION_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VERSION-LIST";
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(3);
        when(novelApplicationService.listChapterVersions(10001L, 3001L)).thenReturn(java.util.List.of(version));

        mockMvc().perform(get("/api/v1/novels/10001/chapters/3001/versions")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].versionNo").value(3));
    }

    @Test
    // 单个章节版本查询成功。
    void UT_NOVEL_VERSION_GET_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-VERSION-GET";
        NovelChapterVersion version = new NovelChapterVersion();
        version.setVersionNo(3);
        when(novelApplicationService.getChapterVersion(10001L, 3001L, 3)).thenReturn(version);

        mockMvc().perform(get("/api/v1/novels/10001/chapters/3001/versions/3")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(3));
    }

    @Test
    // 正文读取URL获取成功。
    void UT_NOVEL_CONTENT_URL_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CONTENT-URL";
        when(novelApplicationService.getChapterContentUrl(10001L, 3001L)).thenReturn(Map.of(
                "url", "https://oss/read"
        ));

        mockMvc().perform(get("/api/v1/novels/10001/chapters/3001/content-url")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value("https://oss/read"))
                .andExpect(jsonPath("$.meta.traceId").value(traceId));
    }

    @Test
    // 大纲树查询成功。
    void UT_NOVEL_OUTLINE_TREE_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-OUTLINE-TREE-LIST";
        NovelOutlineNode node = new NovelOutlineNode();
        node.setId(9001L);
        node.setOutlineNodeId(9001L);
        node.setProjectId(10001L);
        node.setTitle("第一幕");
        when(novelApplicationService.listOutlineTree(10001L)).thenReturn(java.util.List.of(node));

        mockMvc().perform(get("/api/v1/novels/10001/outlines/tree")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].outlineNodeId").isString())
                .andExpect(jsonPath("$.data[0].outlineNodeId").value("9001"))
                .andExpect(jsonPath("$.data[0].title").value("第一幕"));
    }

    @Test
    // 卡片列表查询成功。
    void UT_NOVEL_CARD_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CARD-LIST";
        NovelCard card = new NovelCard();
        card.setId(5001L);
        card.setCardId(5001L);
        card.setProjectId(10001L);
        card.setName("主角");
        when(novelApplicationService.listCards(10001L)).thenReturn(java.util.List.of(card));

        mockMvc().perform(get("/api/v1/novels/10001/cards")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].cardId").isString())
                .andExpect(jsonPath("$.data[0].cardId").value("5001"))
                .andExpect(jsonPath("$.data[0].name").value("主角"));
    }

    @Test
    // 卡片详情查询成功。
    void UT_NOVEL_CARD_GET_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CARD-GET";
        NovelCard card = new NovelCard();
        card.setId(5001L);
        card.setCardId(5001L);
        card.setProjectId(10001L);
        card.setName("主角");
        when(novelApplicationService.getCard(10001L, 5001L)).thenReturn(card);

        mockMvc().perform(get("/api/v1/novels/10001/cards/5001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardId").isString())
                .andExpect(jsonPath("$.data.cardId").value("5001"))
                .andExpect(jsonPath("$.data.name").value("主角"));
    }

    @Test
    // 卡片更新成功。
    void UT_NOVEL_CARD_UPDATE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CARD-UPDATE";
        NovelCard card = new NovelCard();
        card.setId(5001L);
        card.setCardId(5001L);
        card.setProjectId(10001L);
        card.setName("主角-修订");
        when(novelApplicationService.updateCard(eq(10001L), eq(5001L), any(), eq(1001L), eq(traceId))).thenReturn(card);

        mockMvc().perform(put("/api/v1/novels/10001/cards/5001")
                        .param("operatorId", "1001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", traceId)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "cardType", "character",
                                "name", "主角-修订",
                                "summary", "新简介"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardId").isString())
                .andExpect(jsonPath("$.data.cardId").value("5001"))
                .andExpect(jsonPath("$.data.name").value("主角-修订"));
    }

    @Test
    // 卡片删除成功。
    void UT_NOVEL_CARD_DELETE_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-CARD-DELETE";
        doNothing().when(novelApplicationService).deleteCard(10001L, 5001L, 1001L, traceId);

        mockMvc().perform(delete("/api/v1/novels/10001/cards/5001")
                        .param("operatorId", "1001")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("deleted"));
    }

    @Test
    // 关系图谱列表查询成功。
    void UT_NOVEL_RELATION_LIST_SUCCESS() throws Exception {
        String traceId = "UT-TRACE-NOVEL-RELATION-LIST";
        com.penmate.backend.domain.novel.model.NovelCardRelation relation = new com.penmate.backend.domain.novel.model.NovelCardRelation();
        relation.setId(6001L);
        relation.setCardRelationId(6001L);
        relation.setProjectId(10001L);
        when(novelApplicationService.listCardRelations(10001L)).thenReturn(java.util.List.of(relation));

        mockMvc().perform(get("/api/v1/novels/10001/card-relations")
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].relationId").isString())
                .andExpect(jsonPath("$.data[0].relationId").value("6001"));
    }
}

