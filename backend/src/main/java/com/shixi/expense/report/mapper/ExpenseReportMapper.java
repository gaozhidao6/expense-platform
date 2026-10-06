package com.shixi.expense.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.shixi.expense.report.entity.ExpenseReport;
import com.shixi.expense.report.vo.ExpenseReportStatisticsVO;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 报销单主表的数据访问入口。 */
@Mapper
public interface ExpenseReportMapper extends BaseMapper<ExpenseReport> {
    /** Serializes item edits, AI confirmation and submission on the same report. */
    @org.apache.ibatis.annotations.Select("SELECT * FROM expense_report WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    ExpenseReport selectByIdForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

    ExpenseReportStatisticsVO selectStatistics(
        @Param("userId") Long userId,
        @Param("roleCode") String roleCode,
        @Param("startTime") LocalDateTime startTime,
        @Param("endTime") LocalDateTime endTime);

    /**
     * 查询符合导出条件的报销单。
     * 具体 SQL 条件在 ExpenseReportMapper.xml 中逐步实现。
     */
    List<ExpenseReport> selectForExport(
            @Param("userId") Long userId,
            @Param("roleCode") String roleCode,
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
