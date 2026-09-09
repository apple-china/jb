export type RangePreset = 'yesterday' | '7d' | '30d'

function formatInShanghai(date: Date) {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai' }).format(date)
}

export function dateRangePreset(preset: RangePreset, now = new Date()) {
  const yesterday = new Date(now.getTime() - 86_400_000)
  const endDate = formatInShanghai(yesterday)
  const days = preset === 'yesterday' ? 1 : preset === '7d' ? 7 : 30
  return { startDate: formatInShanghai(new Date(now.getTime() - days * 86_400_000)), endDate }
}
