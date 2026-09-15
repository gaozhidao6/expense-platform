package com.shixi.expense.report.service;

import java.util.List;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.shixi.expense.report.vo.ExpenseReportExportRowVO;

/** CSV 文件生成器。 */
@Component
public class ExpenseReportCsvExporter {

    /** 生成带 UTF-8 BOM 的 CSV 字节内容。 */
    public byte[] export(List<ExpenseReportExportRowVO> rows) {
        StringBuilder csv = new StringBuilder("报销单编号,标题,申请人ID,部门ID,总金额,币种,状态,审批轮次,创建时间,完成时间\r\n");
        for (ExpenseReportExportRowVO row : rows) {
            csv.append(cell(row.getReportNo())).append(',')
                    .append(cell(row.getTitle())).append(',')
                    .append(cell(row.getApplicantId())).append(',')
                    .append(cell(row.getDepartmentId())).append(',')
                    .append(cell(row.getTotalAmount())).append(',')
                    .append(cell(row.getCurrency())).append(',')
                    .append(cell(row.getStatus())).append(',')
                    .append(cell(row.getApprovalRound())).append(',')
                    .append(cell(row.getCreatedAt())).append(',')
                    .append(cell(row.getCompletedAt())).append("\r\n");
        }
        // UTF-8 BOM 让 Windows Excel 正确识别中文编码。
        byte[] body = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[body.length + 3];
        result[0] = (byte) 0xEF; result[1] = (byte) 0xBB; result[2] = (byte) 0xBF;
        System.arraycopy(body, 0, result, 3, body.length);
        return result;
    }

    /** CSV 单元格转义：包含逗号、引号或换行时用双引号包裹。 */
    private String cell(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\r") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }
}
