/**
 * 真实 API 优先、演示数据回退。
 *
 * 设计目标：后端可用时走真实接口（前后端联调），后端不可用时页面依然可完整演示，
 * 同时把"当前是演示数据"这一事实返回给调用方，便于界面显式标注，避免把假数据当真结果。
 */
export interface FallbackResult<T> {
  data: T
  demo: boolean
}

export async function withDemoFallback<T>(
  apiCall: () => Promise<T>,
  demoData: () => T,
): Promise<FallbackResult<T>> {
  try {
    const data = await apiCall()
    return { data, demo: false }
  } catch {
    return { data: demoData(), demo: true }
  }
}
