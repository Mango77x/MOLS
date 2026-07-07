import type { ReactNode } from 'react'

export type BadgeTone = 'ok' | 'warn' | 'critical' | 'neutral'

const TONE_CLASSES: Record<BadgeTone, string> = {
  ok: 'bg-status-ok/10 text-status-ok',
  warn: 'bg-status-warn/10 text-status-warn',
  critical: 'bg-status-critical/10 text-status-critical',
  neutral: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300',
}

export default function Badge({ tone, children }: { tone: BadgeTone; children: ReactNode }) {
  return (
    <span className={`rounded px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}>
      {children}
    </span>
  )
}
