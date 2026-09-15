package com.shixi.expense.department.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.department.entity.Department;
import com.shixi.expense.department.mapper.DepartmentMapper;
import com.shixi.expense.department.vo.DepartmentVO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTests {

    @Mock
    private DepartmentMapper departmentMapper;

    @Test
    void pageQueryMapsEntityToStableViewObject() {
        Department department = new Department();
        department.setId(1L);
        department.setName("研发部");
        department.setCode("RND");
        department.setManagerUserId(9L);
        department.setStatus(1);
        department.setSortOrder(10);
        department.setDeleted(1);
        Page<Department> stored = new Page<>(1, 10, 1);
        stored.setRecords(List.of(department));
        when(departmentMapper.selectPage(any(), any())).thenReturn(stored);

        Page<DepartmentVO> result = new DepartmentService(departmentMapper)
                .queryPage(1, 10);

        assertEquals(1, result.getTotal());
        assertEquals("研发部", result.getRecords().get(0).name());
        assertEquals("RND", result.getRecords().get(0).code());
        assertEquals(9L, result.getRecords().get(0).managerUserId());
    }
}
