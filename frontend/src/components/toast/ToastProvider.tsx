import { useCallback, useRef, useState, type ReactNode } from 'react'
import { ToastContext, type ToastTone } from './toastContext'

interface Toast {
  id: number
  message: string
  tone: ToastTone
}

const TONE_CLASS: Record<ToastTone, string> = {
  success: 'border-status-ok/30 bg-status-ok/10 text-status-ok',
  error: 'border-status-critical/30 bg-status-critical/10 text-status-critical',
}

const AUTO_DISMISS_MS = 4000

/** App-wide toast notifications. Wrap the app once with `<ToastProvider>`. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([])
  const nextId = useRef(0)

  const dismiss = useCallback((id: number) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const showToast = useCallback(
    (message: string, tone: ToastTone = 'success') => {
      const id = nextId.current++
      setToasts((current) => [...current, { id, message, tone }])
      setTimeout(() => dismiss(id), AUTO_DISMISS_MS)
    },
    [dismiss],
  )

  return (
    <ToastContext.Provider value={{ showToast }}>
      {children}
      <div
        aria-live="polite"
        aria-atomic="true"
        className="pointer-events-none fixed bottom-4 right-4 z-50 flex flex-col gap-2"
      >
        {toasts.map((toast) => (
          <output
            key={toast.id}
            className={`pointer-events-auto flex items-center gap-3 rounded-lg border px-4 py-2 text-sm shadow-lg ${TONE_CLASS[toast.tone]} bg-white dark:bg-gray-900`}
          >
            <span>{toast.message}</span>
            <button
              type="button"
              onClick={() => dismiss(toast.id)}
              aria-label="Dismiss notification"
              className="text-current opacity-70 hover:opacity-100"
            >
              ✕
            </button>
          </output>
        ))}
      </div>
    </ToastContext.Provider>
  )
}
