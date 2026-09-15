# Day 13：统一异常和响应框架

日期：2026-09-04

## 第一步：代码骨架

已建立以下框架，具体处理逻辑后续实现：

- `ApiResponse<T>`：统一 JSON 响应结构。
- `ErrorCode`：统一错误码枚举。
- `BusinessException`：业务异常类型。
- `GlobalExceptionHandler`：全局异常处理入口。

## 待实现内容

- 完成成功和失败响应工厂方法。
- 根据异常类型设置 HTTP 状态码。
- 处理 `MethodArgumentNotValidException` 和参数绑定异常。
- 记录未知异常日志并隐藏内部堆栈。
- 前端统一处理 400、401、403、404、409、500。

## 已完成补充

- 前端 HTTP 请求已统一识别 400、403、404、409、500，并保留 401 登录过期处理。
- 报销单提交、撤回，以及经理审批、财务审核、付款已加入操作日志入口。
- 业务异常类型替换将按公共校验方法逐步推进，避免一次性改动历史业务代码。

## 异常类型收尾进度

- 驳回意见为空已改为 `BusinessException(BAD_REQUEST)`。
- 时间范围错误已改为 `BusinessException(BAD_REQUEST)`。
- 报销单和审批任务不存在已使用 `BusinessException(NOT_FOUND)`。
- 其余历史异常将在对应业务方法整理时继续替换为 `BusinessException`。
