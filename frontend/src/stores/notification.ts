import { defineStore } from 'pinia'
import { notificationApi } from '@/api/modules'
import { TOKEN_KEY } from '@/api/http'
import { demoNotifications } from '@/mock/demoData'
import type { NotificationDto } from '@/types'

interface NotificationState {
  items: NotificationDto[]
  unread: number
  connected: boolean
}

export const useNotificationStore = defineStore('notification', {
  state: (): NotificationState => ({
    items: [],
    unread: 0,
    connected: false,
  }),
  actions: {
    async load() {
      // 演示模式：直接使用本地演示数据，不请求后端
      const token = localStorage.getItem(TOKEN_KEY)
      if (!token || token.startsWith('demo-')) {
        this.items = [...demoNotifications]
        this.unread = demoNotifications.filter((n) => !n.readFlag).length
        return
      }
      try {
        const res = await notificationApi.list(true)
        this.items = res.items
        this.unread = res.total
      } catch {
        this.items = [...demoNotifications]
        this.unread = demoNotifications.filter((n) => !n.readFlag).length
      }
    },
    setConnected(v: boolean) {
      this.connected = v
    },
    pushEvent(evt: { title: string; content: string; link: string | null }) {
      this.unread += 1
      this.items.unshift({
        id: Date.now(),
        type: 'push',
        title: evt.title,
        content: evt.content,
        link: evt.link,
        readFlag: false,
        createdAt: new Date().toISOString(),
      } as NotificationDto)
    },
    async markRead(id: number) {
      const token = localStorage.getItem(TOKEN_KEY)
      if (token && !token.startsWith('demo-')) {
        try {
          await notificationApi.read(id)
        } catch {
          /* 后端不可用时仍本地置读 */
        }
      }
      const item = this.items.find((i) => i.id === id)
      if (item && !item.readFlag) {
        item.readFlag = true
        this.unread = Math.max(0, this.unread - 1)
      }
    },
    async markAllRead() {
      const token = localStorage.getItem(TOKEN_KEY)
      if (token && !token.startsWith('demo-')) {
        try {
          await notificationApi.readAll()
        } catch {
          /* 后端不可用时仍本地置读 */
        }
      }
      this.items.forEach((i) => (i.readFlag = true))
      this.unread = 0
    },
  },
})
