package com.shixi.expense.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
    @Select("""
        SELECT u.id
        FROM sys_user u
        INNER JOIN sys_user_role ur ON ur.user_id = u.id
        INNER JOIN sys_role r ON r.id = ur.role_id
        WHERE r.code = 'FINANCE'
          AND u.status = 1
        ORDER BY u.id
        LIMIT 1
        """)
Long selectFinanceUserId();

/**
 * 查询一个启用状态的经理用户 ID。
 */
@Select("""
        SELECT u.id
        FROM sys_user u
        INNER JOIN sys_user_role ur
                ON ur.user_id = u.id
        INNER JOIN sys_role r
                ON r.id = ur.role_id
        WHERE r.code = 'MANAGER'
          AND u.status = 1
        ORDER BY u.id
        LIMIT 1
        """)
Long selectEnabledManagerId();
}
