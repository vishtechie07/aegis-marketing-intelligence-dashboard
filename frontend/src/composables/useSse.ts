import { onMounted, onUnmounted } from 'vue'
import { useInsightStore } from '@/stores/insightStore'
import { useSettingsStore } from '@/stores/settingsStore'
import type { Insight } from '@/types/insight'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

export function useSse() {
  const store = useInsightStore()
  let eventSource: EventSource | null = null
  let retryTimer: ReturnType<typeof setTimeout> | null = null
  let retryDelay = 2000

  function connect() {
    store.setStatus('connecting')
    eventSource = new EventSource(`${API_BASE}/api/insights/stream`)

    eventSource.addEventListener('insight', (e: MessageEvent) => {
      const insight: Insight = JSON.parse(e.data)
      store.addInsight(insight)
      retryDelay = 2000
    })

    eventSource.onopen = () => store.setStatus('connected')

    eventSource.onerror = () => {
      store.setStatus('disconnected')
      store.incrementError()
      eventSource?.close()
      retryTimer = setTimeout(() => {
        retryDelay = Math.min(retryDelay * 2, 30000)
        connect()
      }, retryDelay)
    }
  }

  async function loadInitial() {
    const settings = useSettingsStore()
    try {
      const res = await fetch(`${API_BASE}/api/insights/latest?limit=50`)
      if (res.status === 401 || res.status === 403) {
        settings.reportAiKeyIssue(
          'Insights API returned unauthorized. Add or refresh your OpenAI key in Settings.',
        )
        return
      }
      if (res.status === 429) {
        settings.reportAiKeyIssue('Rate limited while loading insights. Try again shortly.')
        return
      }
      if (res.ok) {
        const data: Insight[] = await res.json()
        store.loadInitial(data)
      }
    } catch { /* ignore */ }
  }

  let pollInterval: ReturnType<typeof setInterval> | null = null
  const POLL_MS = 15_000

  onMounted(async () => {
    await loadInitial()
    connect()
    pollInterval = setInterval(loadInitial, POLL_MS)
  })

  onUnmounted(() => {
    if (retryTimer) clearTimeout(retryTimer)
    if (pollInterval) clearInterval(pollInterval)
    eventSource?.close()
  })
}
