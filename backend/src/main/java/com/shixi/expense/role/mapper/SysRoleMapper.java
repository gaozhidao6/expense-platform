package com.shixi.expense.role.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.role.entity.SysRole;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 根据用户 ID 查询该用户拥有的全部角色编码。
     *
     * <p>sys_user_role 保存用户和角色的关联关系，sys_role 保存角色编码。</p>
     *
     * @param userId 用户主键 ID
     * @return 角色编码列表，例如 ADMIN、FINANCE
     */
    @Select("""
            SELECT r.code
            FROM sys_role r
            INNER JOIN sys_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId}
            ORDER BY r.id
            """)
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);
}
