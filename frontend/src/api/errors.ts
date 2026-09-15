/** 从 Axios 错误中提取适合直接展示给用户的中文提示。 */
export function errorMessage(error: any, fallback: string) {
  return error?.userMessage || error?.response?.data?.message || fallback
}
