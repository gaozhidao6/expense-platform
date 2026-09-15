package com.shixi.expense.common.idempotency;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 幂等记录实体，对应数据库表 idempotency_record。
 *
 * <p>表结构已经在 V1__init_schema.sql 中准备好。本实体只保存幂等元数据和响应摘要，
 * 不保存完整的报销业务对象。</p>
 */
@Data
@TableName("idempotency_record")
public class IdempotencyRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String idempotencyKey;
    private Long userId;
    private String businessType;
    private Long businessId;
    private String status;
    private String responseSnapshot;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
