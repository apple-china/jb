/**
 * Formats the account activity timestamp for the compact subtitle used by the
 * account editor. Keeping the boundary rules here makes them easy to test and
 * prevents the settings page from accumulating date arithmetic.
 */
export function formatLastSeen(value?: string, now = Date.now()) {
  if (!value) return '最近上线：从未上线'

  const elapsed = Math.max(0, now - new Date(value).getTime())
  if (elapsed <= 120 * 60_000) {
    return `最近上线：${Math.max(1, Math.floor(elapsed / 60_000))}分钟前`
  }
  if (elapsed <= 48 * 3_600_000) {
    return `最近上线：${Math.max(2, Math.floor(elapsed / 3_600_000))}小时前`
  }
  if (elapsed <= 30 * 86_400_000) {
    return `最近上线：${Math.max(2, Math.floor(elapsed / 86_400_000))}天前`
  }

  return '最近上线：' + new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value)).replaceAll('/', '-')
}
