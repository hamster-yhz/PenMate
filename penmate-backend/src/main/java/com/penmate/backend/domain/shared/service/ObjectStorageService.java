package com.penmate.backend.domain.shared.service;

/**
 * 对象存储访问服务。
 * <p>负责生成对象读取与上传的预签名 URL。</p>
 */
public interface ObjectStorageService {

    /**
     * 上传结果。
     *
     * @param etag ETag
     * @param size 对象大小（字节）
     * @param checksum 校验值（可为空）
     */
    record PutObjectResult(String etag, Long size, String checksum) {
    }

    record ObjectMetadata(String etag, Long size, String checksum, String contentType) {
    }

    /**
     * 生成对象读取预签名 URL。
     *
     * @param objectKey 对象键
     * @return 预签名读取 URL
     */
    String buildReadUrl(String objectKey);

    /**
     * 生成对象上传预签名 URL。
     *
     * @param objectKey 对象键
     * @param contentType 上传内容类型
     * @return 预签名上传 URL
     */
    String buildUploadUrl(String objectKey, String contentType);

    /**
     * 服务端上传文本对象。
     *
     * @param objectKey 对象键
     * @param content 文本内容
     * @param contentType 内容类型
     * @return 上传结果
     */
    PutObjectResult putText(String objectKey, String content, String contentType);

    PutObjectResult putBytes(String objectKey, byte[] content, String contentType);

    /**
     * 服务端读取文本对象内容。
     *
     * @param objectKey 对象键
     * @return 文本内容
     */
    String readText(String objectKey);

    byte[] readBytes(String objectKey);

    ObjectMetadata head(String objectKey);

    boolean exists(String objectKey);

    void delete(String objectKey);
}

