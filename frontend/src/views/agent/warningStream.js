import { fetchEventSource } from '@microsoft/fetch-event-source'
import { createBackoff } from '@/utils/backoff'

class StreamAccessDenied extends Error {}
class StreamEnded extends Error {}

// EOF is retryable; explicit authentication/authorization failures are terminal.
// The transport is injected in offline tests, including stale/aborted callbacks.
export const createWarningStream = ({
  url,
  getToken,
  onConnected,
  onWarning,
  transport = fetchEventSource,
  backoff = createBackoff({ min: 2000, max: 60000 })
}) => {
  let controller = null
  let disposed = false
  const stop = () => {
    controller?.abort()
    controller = null
    onConnected(false)
  }
  const start = async () => {
    stop()
    const token = getToken()
    if (disposed || !token) return
    const current = new AbortController()
    controller = current
    const active = () => controller === current && !current.signal.aborted && !disposed
    try {
      await transport(url, {
        signal: current.signal,
        headers: { Authorization: `Bearer ${token}` },
        openWhenHidden: true,
        onopen: async (response) => {
          if (!active()) return
          if (response.status === 401 || response.status === 403) throw new StreamAccessDenied()
          if (!response.ok || !response.headers.get('content-type')?.includes('text/event-stream'))
            throw new Error('SSE handshake unavailable')
          onConnected(true)
          backoff.reset()
        },
        onmessage: (event) => {
          if (active() && event.event === 'warning') onWarning()
        },
        onclose: () => {
          if (active()) {
            onConnected(false)
            throw new StreamEnded()
          }
        },
        onerror: (error) => {
          if (!active()) throw error
          onConnected(false)
          if (error instanceof StreamAccessDenied) throw error
          return backoff.next()
        }
      })
    } catch {
      if (active()) onConnected(false)
    }
  }
  return {
    start,
    stop,
    dispose() {
      disposed = true
      stop()
    }
  }
}
