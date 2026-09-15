package com.shixi.expense.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.operation.entity.OperationLog;
import com.shixi.expense.operation.mapper.OperationLogMapper;
import com.shixi.expense.operation.service.OperationLogWriteService;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class OperationLogWriteServiceTests {

    @Mock private OperationLogMapper operationLogMapper;
    @Mock private SysUserMapper sysUserMapper;

    @Test
    void failureAuditUsesIndependentTransactionAndNormalizesMessage() throws Exception {
        Transactional transactional = OperationLogWriteService.class
                .getMethod("recordFailure", String.class, String.class, Long.class, String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertEquals(Propagation.REQUIRES_NEW, transactional.propagation());

        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        OperationLogWriteService service = new OperationLogWriteService(
                operationLogMapper, sysUserMapper);
        service.recordFailure("MANAGER_REJECT", "EXPENSE_REPORT", 10L,
                "状态冲突\n请刷新后重试");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("FAILURE", captor.getValue().getResult());
        assertEquals("状态冲突 请刷新后重试", captor.getValue().getErrorMessage());
    }
}
