import { describe, expect, it } from 'vitest'
import { dateRangePreset } from './dateRanges'

describe('dateRangePreset', () => {
  const now = new Date('2026-09-07T06:22:00Z')
  it('returns yesterday as a single day', () => expect(dateRangePreset('yesterday', now)).toEqual({ startDate: '2026-09-06', endDate: '2026-09-06' }))
  it('returns seven completed days excluding today', () => expect(dateRangePreset('7d', now)).toEqual({ startDate: '2026-08-31', endDate: '2026-09-06' }))
  it('returns thirty completed days excluding today', () => expect(dateRangePreset('30d', now)).toEqual({ startDate: '2026-08-08', endDate: '2026-09-06' }))
})
