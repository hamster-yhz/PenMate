package com.penmate.backend.interfaces.api.rag;

import com.penmate.backend.application.rag.RagApplicationService;
import com.penmate.backend.application.rag.command.CreateRagDocumentCommand;
import com.penmate.backend.application.rag.command.OperateRagDocumentCommand;
import com.penmate.backend.domain.rag.model.RagDocument;
import com.penmate.backend.interfaces.api.common.ApiResponse;
import com.penmate.backend.interfaces.api.rag.dto.CreateRagDocumentDto;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * RAG 文档与检索日志控制器。
 * <p>负责知识文档的创建、解析、向量化、索引状态查询，以及检索日志查询接口。</p>
 */
@RestController
@RequestMapping("/api/v1/novels/{projectId}/rag")
public class RagController {

    private final RagApplicationService ragApplicationService;

    public RagController(RagApplicationService ragApplicationService) {
        this.ragApplicationService = ragApplicationService;
    }

    /**
     * 查询项目知识库文档列表。
     * <p><b>业务目的：</b>返回项目下所有 RAG 文档元数据，供知识库管理页展示。</p>
     * <p><b>流程主线：</b>读取项目参数 -> 调用应用服务查询文档 -> 统一封装响应。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.listDocuments(projectId)}。</p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/documents")
    public ApiResponse<List<RagDocument>> listDocuments(@PathVariable Long projectId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.listDocuments(projectId), traceId);
    }

    /**
     * 创建知识文档记录。
     * <p><b>业务目的：</b>登记待解析文档的来源信息与原始对象存储引用，进入后续解析与向量化流程。</p>
     * <p><b>流程主线：</b>校验请求体 -> 组装 {@link CreateRagDocumentCommand} -> 调用应用服务创建文档 -> 返回文档信息。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.createDocument(projectId, command, traceId)}。</p>
     * <p><b>异常与分支：</b>文档类型非法、项目无权限或来源信息缺失时返回业务异常。</p>
     * <p><b>副作用：</b>新增文档记录。</p>
     */
    @PostMapping("/documents")
    public ApiResponse<RagDocument> createDocument(@PathVariable Long projectId,
                                                   @Valid @RequestBody CreateRagDocumentDto dto,
                                                   @RequestParam("operatorId") Long operatorId,
                                                   @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        CreateRagDocumentCommand command = new CreateRagDocumentCommand(
                dto.getDocType(),
                dto.getTitle(),
                dto.getSourceRef(),
                dto.getOriginObjectKey(),
                dto.getOriginEtag(),
                dto.getMimeType(),
                operatorId
        );
        return ApiResponse.success(ragApplicationService.createDocument(projectId, command, traceId), traceId);
    }

    /**
     * 查询文档详情。
     * <p><b>业务目的：</b>返回单个文档的状态、来源与索引相关信息，支持文档详情页展示。</p>
     * <p><b>流程主线：</b>接收文档ID -> 调用应用服务查询详情 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.getDocument(projectId, docId)}。</p>
     * <p><b>异常与分支：</b>文档不存在或不属于项目时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/documents/{docId}")
    public ApiResponse<RagDocument> getDocument(@PathVariable Long projectId,
                                                @PathVariable Long docId,
                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getDocument(projectId, docId), traceId);
    }

    /**
     * 删除知识文档。
     * <p><b>业务目的：</b>移除错误或过期文档，避免其继续参与检索与生成。</p>
     * <p><b>流程主线：</b>组装 {@link OperateRagDocumentCommand} -> 调用应用服务执行删除 -> 返回确认结果。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.deleteDocument(projectId, docId, command, traceId)}。</p>
     * <p><b>异常与分支：</b>文档不存在、状态不可删或权限不足时返回业务异常。</p>
     * <p><b>副作用：</b>删除文档元数据及关联索引数据。</p>
     */
    @DeleteMapping("/documents/{docId}")
    public ApiResponse<String> deleteDocument(@PathVariable Long projectId,
                                              @PathVariable Long docId,
                                              @RequestParam("operatorId") Long operatorId,
                                              @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(operatorId);
        ragApplicationService.deleteDocument(projectId, docId, command, traceId);
        return ApiResponse.success("deleted", traceId);
    }

    /**
     * 获取文档上传地址。
     * <p><b>业务目的：</b>为前端直传文档到对象存储生成上传 URL 与必要参数。</p>
     * <p><b>流程主线：</b>读取项目参数 -> 调用应用服务申请上传地址 -> 返回地址信息。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.getDocumentUploadUrl(projectId)}。</p>
     * <p><b>异常与分支：</b>存储服务不可用时返回业务异常。</p>
     * <p><b>副作用：</b>可能创建短时上传凭证。</p>
     */
    @PostMapping("/documents/upload-url")
    public ApiResponse<Map<String, String>> getUploadUrl(@PathVariable Long projectId,
                                                          @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getDocumentUploadUrl(projectId), traceId);
    }

    /**
     * 触发文档解析。
     * <p><b>业务目的：</b>将原始文档转换为可切分文本，为后续向量化建立输入。</p>
     * <p><b>流程主线：</b>组装操作命令 -> 调用应用服务执行解析 -> 返回最新文档状态。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.parseDocument(projectId, docId, command, traceId)}。</p>
     * <p><b>异常与分支：</b>文档状态不允许解析或解析失败时返回业务异常。</p>
     * <p><b>副作用：</b>更新文档解析状态并写入解析产物。</p>
     */
    @PostMapping("/documents/{docId}/parse")
    public ApiResponse<RagDocument> parseDocument(@PathVariable Long projectId,
                                                  @PathVariable Long docId,
                                                  @RequestParam("operatorId") Long operatorId,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(operatorId);
        return ApiResponse.success(ragApplicationService.parseDocument(projectId, docId, command, traceId), traceId);
    }

    /**
     * 触发文档向量化。
     * <p><b>业务目的：</b>对已解析文本执行 embedding，建立向量索引以支持检索增强。</p>
     * <p><b>流程主线：</b>组装操作命令 -> 调用应用服务执行向量化 -> 返回更新后的文档状态。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.embedDocument(projectId, docId, command, traceId)}。</p>
     * <p><b>异常与分支：</b>解析未完成、文档不可用或向量服务失败时返回业务异常。</p>
     * <p><b>副作用：</b>写入向量索引并更新文档索引状态。</p>
     */
    @PostMapping("/documents/{docId}/embed")
    public ApiResponse<RagDocument> embedDocument(@PathVariable Long projectId,
                                                  @PathVariable Long docId,
                                                  @RequestParam("operatorId") Long operatorId,
                                                  @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        OperateRagDocumentCommand command = new OperateRagDocumentCommand(operatorId);
        return ApiResponse.success(ragApplicationService.embedDocument(projectId, docId, command, traceId), traceId);
    }

    /**
     * 查询文档索引状态。
     * <p><b>业务目的：</b>返回解析/向量化阶段状态与统计信息，便于前端展示处理进度。</p>
     * <p><b>流程主线：</b>读取文档标识 -> 调用应用服务获取索引状态 -> 封装响应。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.getIndexStatus(projectId, docId)}。</p>
     * <p><b>异常与分支：</b>文档不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/documents/{docId}/index-status")
    public ApiResponse<Map<String, Object>> indexStatus(@PathVariable Long projectId,
                                                        @PathVariable Long docId,
                                                        @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.getIndexStatus(projectId, docId), traceId);
    }

    /**
     * 查询检索日志列表。
     * <p><b>业务目的：</b>返回项目检索调用记录，支持检索质量追踪与问题回放。</p>
     * <p><b>流程主线：</b>读取项目ID -> 调用应用服务查询日志 -> 返回日志列表。</p>
     * <p><b>关键调用：</b>{@code ragApplicationService.listRetrievalLogs(projectId)}。</p>
     * <p><b>异常与分支：</b>项目不存在时返回业务异常。</p>
     * <p><b>副作用：</b>无持久化写入。</p>
     */
    @GetMapping("/retrieval-logs")
    public ApiResponse<List<Map<String, Object>>> retrievalLogs(@PathVariable Long projectId,
                                                                @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return ApiResponse.success(ragApplicationService.listRetrievalLogs(projectId), traceId);
    }
}

