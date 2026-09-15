package com.shixi.expense.operation.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.shixi.expense.operation.dto.OperationLogPageQueryDTO;
import com.shixi.expense.operation.service.OperationLogService;
import com.shixi.expense.operation.vo.OperationLogVO;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 操作日志查询接口框架。 */
@RestController
@RequestMapping("/api/operation-logs")
public class OperationLogController {

    private final OperationLogService operationLogService;

    public OperationLogController(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    /** 查询操作日志分页列表。 */
    @GetMapping
    public IPage<OperationLogVO> queryPage(
            @Valid OperationLogPageQueryDTO query) {
        return operationLogService.queryPage(query);
    }
}
