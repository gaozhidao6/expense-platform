package com.shixi.expense.user.service;

import com.shixi.expense.user.mapper.SysUserMapper;

import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shixi.expense.user.dto.UserPageQueryDTO;
import com.shixi.expense.user.entity.SysUser;
import com.shixi.expense.user.vo.UserVO;
import org.springframework.util.StringUtils;

/**
 * 用户业务服务。
 *
 * <p>Service 负责组织业务流程、调用 Mapper 和 Entity/VO 转换，
 * 不直接处理 HTTP 请求。</p>
 */
@Service
public class SysUserService {

    private final SysUserMapper sysUserMapper;

    public SysUserService(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    /** 根据关键字分页查询用户，并在返回前转换为不含密码摘要的 VO。 */
    public IPage<UserVO> queryPage(UserPageQueryDTO query) {
        // 创建分页对象；页码和每页数量已经由 DTO 参数校验约束。
        Page<SysUser> pageRequest =
        new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            String keyword = query.getKeyword().trim();
            wrapper.and(condition -> condition
                    .like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getRealName, keyword)
                    .or()
                    .like(SysUser::getEmployeeNo, keyword));
        }
        wrapper.orderByDesc(SysUser::getCreatedAt);
        IPage<SysUser> entityPage = sysUserMapper.selectPage(pageRequest, wrapper);
        return entityPage.convert(this::toVO);
    }
    /** Entity 转 VO 时明确排除 passwordHash 等内部认证字段。 */
    private UserVO toVO(SysUser user) {
        UserVO vo = new UserVO();

        vo.setId(user.getId());
        vo.setDepartmentId(user.getDepartmentId());
        vo.setEmployeeNo(user.getEmployeeNo());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setEmail(user.getEmail());
        vo.setMobile(user.getMobile());
        vo.setStatus(user.getStatus());
        vo.setLastLoginAt(user.getLastLoginAt());
        vo.setCreatedAt(user.getCreatedAt());
        vo.setUpdatedAt(user.getUpdatedAt());

        return vo;
    }
}
