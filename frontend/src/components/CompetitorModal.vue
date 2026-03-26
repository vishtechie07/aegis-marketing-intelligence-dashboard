<script setup lang="ts">
import { ref, onMounted, computed } from "vue"
import { useCompetitorStore, type Competitor } from "@/stores/competitorStore"
import { useSettingsStore } from "@/stores/settingsStore"
import { COUNTRIES } from "@/data/countries"
import { countryFlag } from "@/data/countryFlags"

const emit = defineEmits<{ close: [] }>()

const store = useCompetitorStore()
const settings = useSettingsStore()

const nameInput = ref("")
const countrySearch = ref("")
const showCountryDropdown = ref(false)
const lookupDone = ref(false)
const pending = ref<Competitor | null>(null)

const manualTicker = ref("")
const manualIndustry = ref("")

const INDUSTRIES = ["tech","finance","defence","agriculture","textiles","healthcare","energy","retail","media","other"]

onMounted(() => store.fetchAll())

const filteredCountries = computed(() =>
  countrySearch.value.trim()
    ? COUNTRIES.filter(c => c.toLowerCase().includes(countrySearch.value.toLowerCase()))
    : COUNTRIES
)

function delayHideDropdown() {
  window.setTimeout(() => { showCountryDropdown.value = false }, 150)
}

function selectCountry(c: string) {
  countrySearch.value = c
  showCountryDropdown.value = false
  if (pending.value) pending.value.country = c
}

function clearForm() {
  nameInput.value = ""
  countrySearch.value = ""
  manualTicker.value = ""
  manualIndustry.value = ""
  pending.value = null
  lookupDone.value = false
  store.error = null
}

async function handleLookup() {
  if (!nameInput.value.trim()) return
  lookupDone.value = false
  const country = countrySearch.value.trim() || undefined
  const result = await store.lookup(nameInput.value.trim(), country)
  if (result) {
    pending.value = { ...result }
    if (country && !result.country) pending.value.country = country
    if (result.country) countrySearch.value = result.country
    lookupDone.value = true
  }
}

async function handleAdd() {
  const competitor: Competitor = pending.value ?? {
    name: nameInput.value.trim(),
    githubOrg: nameInput.value.trim().toLowerCase().replaceAll(" ", "-"),
    ticker: manualTicker.value.trim() || null,
    industry: manualIndustry.value || null,
    country: countrySearch.value.trim() || null,
    description: null,
  }
  if (!competitor.name) return
  const ok = await store.add(competitor)
  if (ok) clearForm()
}

async function handleRemove(name: string) {
  await store.remove(name)
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") emit("close")
}
</script>

<template>
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm p-4"
    @click.self="emit('close')"
    @keydown="handleKeydown"
  >
    <div
      class="flex w-full max-w-2xl flex-col rounded-xl bg-gray-900 shadow-2xl ring-1 ring-gray-700 max-h-[90vh]"
      role="dialog"
      aria-modal="true"
      aria-labelledby="competitors-title"
      aria-describedby="competitors-desc"
    >

      <div class="flex items-center justify-between border-b border-gray-800 px-6 py-4 shrink-0">
        <div>
          <h2 id="competitors-title" class="text-base font-semibold text-white">Competitors</h2>
          <p id="competitors-desc" class="mt-0.5 text-xs text-gray-500">Manage tracked companies — changes apply to all harvesters immediately</p>
        </div>
        <button class="rounded-md p-1.5 text-gray-500 hover:bg-gray-800 hover:text-gray-300 transition-colors" @click="emit('close')">
          <svg class="size-4" viewBox="0 0 20 20" fill="currentColor"><path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z"/></svg>
        </button>
      </div>

      <div class="flex flex-col gap-5 overflow-y-auto px-6 py-5">

        <div class="rounded-lg bg-gray-800/50 p-4 ring-1 ring-gray-700 space-y-3">
          <p class="text-xs font-medium text-gray-400 uppercase tracking-wider">Add Competitor</p>

          <div class="flex gap-3">
            <div class="flex-1">
              <label class="mb-1 block text-xs text-gray-500">Company / Business Name *</label>
              <input
                v-model="nameInput"
                placeholder="e.g. Stripe"
                class="w-full rounded-lg bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-600 outline-none ring-1 ring-gray-700 focus:ring-blue-500"
                @keydown.enter="settings.isConfigured ? handleLookup() : handleAdd()"
              />
            </div>
            <div class="flex-1 relative">
              <label class="mb-1 block text-xs text-gray-500">Country of Origin (optional)</label>
              <div class="relative">
                <span v-if="countrySearch && countryFlag(countrySearch)" class="absolute left-2.5 top-1/2 -translate-y-1/2 text-base leading-none pointer-events-none">
                  {{ countryFlag(countrySearch) }}
                </span>
                <input
                  v-model="countrySearch"
                  placeholder="Type to search..."
                  class="w-full rounded-lg bg-gray-800 py-2 pr-3 text-sm text-gray-100 placeholder-gray-600 outline-none ring-1 ring-gray-700 focus:ring-blue-500"
                  :class="countrySearch && countryFlag(countrySearch) ? 'pl-8' : 'pl-3'"
                  @focus="showCountryDropdown = true"
                  @blur="delayHideDropdown"
                />
              </div>
              <ul
                v-if="showCountryDropdown && filteredCountries.length"
                class="absolute z-10 mt-1 max-h-44 w-full overflow-y-auto rounded-lg bg-gray-800 ring-1 ring-gray-700 shadow-xl text-sm"
              >
                <li
                  v-for="c in filteredCountries"
                  :key="c"
                  class="flex items-center gap-2 cursor-pointer px-3 py-2 text-gray-300 hover:bg-gray-700 hover:text-white"
                  @mousedown.prevent="selectCountry(c)"
                >
                  <span class="text-base w-6 text-center shrink-0">{{ countryFlag(c) }}</span>
                  <span>{{ c }}</span>
                </li>
              </ul>
            </div>
          </div>

          <div v-if="!lookupDone" class="flex gap-3">
            <div class="flex-1">
              <label class="mb-1 block text-xs text-gray-500">Stock Ticker (optional)</label>
              <input
                v-model="manualTicker"
                placeholder="e.g. MSFT, AAPL"
                class="w-full rounded-lg bg-gray-800 px-3 py-2 text-sm text-gray-100 placeholder-gray-600 outline-none ring-1 ring-gray-700 focus:ring-blue-500 font-mono uppercase"
              />
            </div>
            <div class="flex-1">
              <label class="mb-1 block text-xs text-gray-500">Industry (optional)</label>
              <select
                v-model="manualIndustry"
                class="w-full rounded-lg bg-gray-800 px-3 py-2 text-sm text-gray-100 outline-none ring-1 ring-gray-700 focus:ring-blue-500 capitalize"
              >
                <option value="">Select industry...</option>
                <option v-for="ind in INDUSTRIES" :key="ind" :value="ind" class="capitalize">{{ ind }}</option>
              </select>
            </div>
          </div>

          <div v-if="lookupDone && pending" class="rounded-lg bg-blue-950/40 px-4 py-3 ring-1 ring-blue-800/40 space-y-2">
            <p class="text-xs font-medium text-blue-300">AI found:</p>
            <div class="flex items-center gap-2">
              <span v-if="pending.country" class="text-xl">{{ countryFlag(pending.country) }}</span>
              <p class="text-sm text-white font-semibold">{{ pending.name }}</p>
              <span v-if="pending.industry" class="text-xs text-gray-400 bg-gray-700 px-2 py-0.5 rounded capitalize">{{ pending.industry }}</span>
            </div>
            <div class="grid grid-cols-2 gap-2 text-xs text-gray-400">
              <span>GitHub: <code class="text-blue-300">{{ pending.githubOrg }}</code></span>
              <span v-if="pending.ticker">Ticker: <code class="text-green-300">{{ pending.ticker }}</code></span>
              <span v-if="pending.country">Country: {{ pending.country }}</span>
            </div>
            <p v-if="pending.description" class="text-xs text-gray-400">{{ pending.description }}</p>
            <div class="flex gap-2 items-center pt-1">
              <label class="text-xs text-gray-500 shrink-0">GitHub org:</label>
              <input v-model="pending.githubOrg" class="flex-1 rounded bg-gray-800 px-2 py-1 text-xs text-gray-300 ring-1 ring-gray-700 focus:ring-blue-500 outline-none font-mono" placeholder="github-org-slug"/>
              <label class="text-xs text-gray-500 shrink-0">Ticker:</label>
              <input v-model="pending.ticker" class="w-20 rounded bg-gray-800 px-2 py-1 text-xs text-gray-300 ring-1 ring-gray-700 focus:ring-blue-500 outline-none font-mono uppercase" placeholder="TICK"/>
            </div>
          </div>

          <p v-if="store.error" class="text-xs text-red-400 rounded bg-red-950/40 px-3 py-2 ring-1 ring-red-900">{{ store.error }}</p>

          <div class="flex gap-3 justify-end">
            <button
              v-if="settings.isConfigured && !lookupDone"
              class="rounded-md bg-gray-700 px-4 py-2 text-sm text-gray-200 hover:bg-gray-600 disabled:opacity-40 transition-colors flex items-center gap-2"
              :disabled="!nameInput.trim() || store.lookupLoading"
              @click="handleLookup"
            >
              <svg v-if="store.lookupLoading" class="size-3.5 animate-spin" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 2v4M12 18v4M4.93 4.93l2.83 2.83M16.24 16.24l2.83 2.83M2 12h4M18 12h4M4.93 19.07l2.83-2.83M16.24 7.76l2.83-2.83"/></svg>
              {{ store.lookupLoading ? "Looking up..." : "✦ AI Lookup" }}
            </button>
            <button v-if="lookupDone" class="text-sm text-gray-500 hover:text-gray-300 px-3 py-2 transition-colors" @click="clearForm">Reset</button>
            <button
              class="rounded-md bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-500 disabled:opacity-40 transition-colors"
              :disabled="!nameInput.trim() && !pending"
              @click="handleAdd"
            >+ Add Competitor</button>
          </div>
        </div>

        <div>
          <p class="mb-3 text-xs font-medium text-gray-400 uppercase tracking-wider">Tracked ({{ store.list.length }})</p>
          <div v-if="store.loading" class="text-xs text-gray-500 py-4 text-center">Loading...</div>
          <ul v-else class="space-y-2">
            <li
              v-for="c in store.list" :key="c.name"
              class="flex items-center justify-between rounded-lg bg-gray-800/40 px-4 py-3 ring-1 ring-gray-700/60"
            >
              <div class="flex items-center gap-3 min-w-0">
                <span v-if="c.country" class="text-xl shrink-0">{{ countryFlag(c.country) }}</span>
                <div class="min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <p class="text-sm font-medium text-white">{{ c.name }}</p>
                    <span v-if="c.ticker" class="text-xs font-mono text-green-400 bg-green-950/40 px-1.5 py-0.5 rounded ring-1 ring-green-900/40">{{ c.ticker }}</span>
                    <span v-if="c.industry" class="text-xs text-gray-500 bg-gray-700/50 px-1.5 py-0.5 rounded capitalize">{{ c.industry }}</span>
                  </div>
                  <p class="text-xs text-gray-500 font-mono">{{ c.githubOrg }}</p>
                </div>
              </div>
              <button class="text-xs text-gray-600 hover:text-red-400 transition-colors px-2 py-1 rounded hover:bg-red-950/30 shrink-0 ml-2" @click="handleRemove(c.name)">Remove</button>
            </li>
            <li v-if="!store.list.length" class="text-center text-xs text-gray-600 py-4">No competitors tracked yet</li>
          </ul>
        </div>
      </div>

      <div class="border-t border-gray-800 px-6 py-4 shrink-0">
        <p class="text-xs text-gray-600">
          Changes take effect on the next harvester run. Ticker enables Yahoo Finance data. Industry enables macro context matching.
          <span v-if="!settings.isConfigured" class="text-amber-500"> AI Lookup requires an OpenAI key.</span>
        </p>
      </div>
    </div>
  </div>
</template>
