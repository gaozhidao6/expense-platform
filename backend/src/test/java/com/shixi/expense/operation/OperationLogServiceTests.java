package com.shixi.expense.operation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.operation.dto.OperationLogPageQueryDTO;
import com.shixi.expense.operation.entity.OperationLog;
import com.shixi.expense.operation.mapper.OperationLogMapper;
import com.shixi.expense.operation.service.OperationLogService;
import com.shixi.expense.operation.vo.OperationLogVO;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTests {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private OperationLogMapper operationLogMapper;

    private OperationLogService service;

    @BeforeEach
    void setUp() {
        service = new OperationLogService(sysUserMapper, operationLogMapper);
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setStatus(1);
        lenient().when(sysUserMapper.selectOne(any())).thenReturn(user);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanQueryLogsAndReceivesMappedRows() {
        authenticate("admin", "ROLE_ADMIN");
        OperationLog log = new OperationLog();
        log.setId(7L);
        log.setOperatorId(1L);
        log.setOperation("FINANCE_APPROVE");
        log.setResult("SUCCESS");
        log.setRequestId("request-1");
        Page<OperationLog> stored = new Page<>();
        stored.setRecords(java.util.List.of(log));
        stored.setTotal(1);
        when(operationLogMapper.selectPage(any(), any())).thenReturn(stored);

        OperationLogPageQueryDTO query = new OperationLogPageQueryDTO();
        query.setOperatorId(1L);
        query.setOperation("FINANCE_APPROVE");
        IPage<OperationLogVO> result = service.queryPage(query);

        assertEquals(1, result.getTotal());
        assertEquals("FINANCE_APPROVE", result.getRecords().get(0).getOperation());
        assertEquals("request-1", result.getRecords().get(0).getRequestId());
    }

    @Test
    void employeeCannotQueryLogs() {
        authenticate("employee", "ROLE_EMPLOYEE");
        assertThrows(AccessDeniedException.class,
                () -> service.queryPage(new OperationLogPageQueryDTO()));
    }

    private void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        username,
                        "N/A",
                        java.util.List.of(new SimpleGrantedAuthority(role))));
    }
}
