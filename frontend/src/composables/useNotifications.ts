import { onMounted, onUnmounted } from 'vue'
import { useNotificationStore } from '@/stores/notification'
import { TOKEN_KEY } from '@/api/http'

// SSE over fetch + ReadableStream (EventSource cannot carry Authorization header).
// Exponential backoff reconnect. Falls back silently to polling (store.load) if stream fails.
export function useNotifications() {
  const store = useNotificationStore()
  let controller: AbortController | null = null
  let retryDelay = 1000
  let stopped = false

  async function connect() {
    if (stopped) return
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token || token.startsWith('demo-')) return // 演示模式跳过 SSE
    controller = new AbortController()
    try {
      const res = await fetch('/api/notifications/stream', {
        headers: { Authorization: `Bearer ${token}` },
        signal: controller.signal,
      })
      if (!res.ok || !res.body) throw new Error('stream not available')
      store.setConnected(true)
      retryDelay = 1000
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const events = buffer.split('\n\n')
        buffer = events.pop() ?? ''
        for (const chunk of events) {
          const line = chunk.split('\n').find((l) => l.startsWith('data:'))
          if (!line) continue
          try {
            const evt = JSON.parse(line.slice(5).trim())
            if (evt && evt.title) store.pushEvent(evt)
          } catch {
            /* ignore malformed events */
          }
        }
      }
    } catch {
      /* stream dropped */
    } finally {
      store.setConnected(false)
      if (!stopped) {
        setTimeout(connect, retryDelay)
        retryDelay = Math.min(retryDelay * 2, 30000)
      }
    }
  }

  onMounted(() => {
    connect()
  })
  onUnmounted(() => {
    stopped = true
    controller?.abort()
  })

  return { connect }
}
