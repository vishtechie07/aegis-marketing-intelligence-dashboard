import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useInsightStore } from './insightStore'
import type { Insight } from '@/types/insight'

function makeInsight(id: number, threatLevel = 5, competitorName = 'Acme'): Insight {
  return {
    id, newsId: id * 10, competitorName, title: `Title ${id}`,
    sourceUrl: `https://example.com/${id}`, sourceType: 'RSS',
    agentName: 'Strategist', category: 'PRODUCT_LAUNCH',
    threatLevel, summary: 'Summary', strategicAdvice: 'Advice',
    publishedAt: new Date().toISOString(), processedAt: new Date().toISOString(),
  }
}

describe('insightStore', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('starts empty', () => {
    const store = useInsightStore()
    expect(store.insights).toHaveLength(0)
    expect(store.connectionStatus).toBe('disconnected')
  })

  it('addInsight prepends and sets isNew flag', () => {
    const store = useInsightStore()
    store.addInsight(makeInsight(1))

    expect(store.insights[0].id).toBe(1)
    expect(store.insights[0].isNew).toBe(true)
  })

  it('addInsight clears isNew flag after 3 seconds', async () => {
    vi.useFakeTimers()
    const store = useInsightStore()
    store.addInsight(makeInsight(1))

    expect(store.insights[0].isNew).toBe(true)
    vi.advanceTimersByTime(3001)
    expect(store.insights[0].isNew).toBe(false)
    vi.useRealTimers()
  })

  it('addInsight caps list at 50 items', () => {
    const store = useInsightStore()
    for (let i = 1; i <= 55; i++) store.addInsight(makeInsight(i))
    expect(store.insights).toHaveLength(50)
  })

  it('loadInitial replaces all insights with isNew=false', () => {
    const store = useInsightStore()
    store.addInsight(makeInsight(1))
    store.loadInitial([makeInsight(10), makeInsight(11)])

    expect(store.insights).toHaveLength(2)
    expect(store.insights[0].isNew).toBe(false)
  })

  it('highThreatInsights filters threatLevel >= 7', () => {
    const store = useInsightStore()
    store.loadInitial([
      makeInsight(1, 5),
      makeInsight(2, 7),
      makeInsight(3, 9),
    ])

    expect(store.highThreatInsights).toHaveLength(2)
    expect(store.highThreatInsights.every(i => i.threatLevel >= 7)).toBe(true)
  })

  it('insightsByCompetitor groups correctly', () => {
    const store = useInsightStore()
    store.loadInitial([
      makeInsight(1, 5, 'OpenAI'),
      makeInsight(2, 5, 'Google'),
      makeInsight(3, 5, 'OpenAI'),
    ])

    const map = store.insightsByCompetitor
    expect(map.get('OpenAI')).toHaveLength(2)
    expect(map.get('Google')).toHaveLength(1)
  })

  it('setStatus updates connectionStatus', () => {
    const store = useInsightStore()
    store.setStatus('connected')
    expect(store.connectionStatus).toBe('connected')
  })

  it('incrementError increments errorCount', () => {
    const store = useInsightStore()
    store.incrementError()
    store.incrementError()
    expect(store.errorCount).toBe(2)
  })
})
