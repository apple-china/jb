import { describe, expect, it } from 'vitest'
import { formatLastSeen } from './lastSeen'

describe('formatLastSeen', () => {
  const now = new Date('2026-09-08T08:00:00Z').getTime()
  const ago = (milliseconds: number) => new Date(now - milliseconds).toISOString()

  it('uses minute, hour and day labels at the confirmed boundaries', () => {
    expect(formatLastSeen(ago(60_000), now)).toBe('最近上线：1分钟前')
    expect(formatLastSeen(ago(120 * 60_000), now)).toBe('最近上线：120分钟前')
    expect(formatLastSeen(ago(121 * 60_000), now)).toBe('最近上线：2小时前')
    expect(formatLastSeen(ago(48 * 3_600_000), now)).toBe('最近上线：48小时前')
    expect(formatLastSeen(ago(49 * 3_600_000), now)).toBe('最近上线：2天前')
    expect(formatLastSeen(ago(30 * 86_400_000), now)).toBe('最近上线：30天前')
  })

  it('uses an absolute Shanghai timestamp after thirty days', () => {
    expect(formatLastSeen(ago(31 * 86_400_000), now)).toBe('最近上线：2026-08-08 16:00')
    expect(formatLastSeen(undefined, now)).toBe('最近上线：从未上线')
  })
})
