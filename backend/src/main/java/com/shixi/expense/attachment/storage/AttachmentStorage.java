package com.shixi.expense.attachment.storage;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.core.io.Resource;

/**
 * 附件文件存储接口。
 *
 * <p>业务层只依赖该接口，暂不绑定本地目录、MinIO 等具体存储实现。</p>
 */
public interface AttachmentStorage {

    /**
     * 保存文件内容，并返回相对路径或对象 key。
     *
     * @param inputStream 文件输入流
     * @param objectKey 由业务层规划的安全存储 key
     * @return 文件保存后的相对路径或对象 key
     * @throws IOException 文件读写失败
     */
    String save(InputStream inputStream, String objectKey) throws IOException;

    /**
     * 读取指定文件，供下载接口使用。
     *
     * @param objectKey 文件相对路径或对象 key
     * @return 可由 Spring MVC 输出的文件资源
     * @throws IOException 文件读取失败
     */
    Resource load(String objectKey) throws IOException;

    /**
     * 删除指定文件。
     *
     * @param objectKey 文件相对路径或对象 key
     * @throws IOException 文件删除失败
     */
    void delete(String objectKey) throws IOException;
}
