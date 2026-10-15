const DEFAULT_CONSOLE_URL = 'https://jp.cloud.langfuse.com'

export function normalizeConsoleUrl(configured) {
  try {
    const url = new URL(configured?.trim() || DEFAULT_CONSOLE_URL)
    if (url.protocol !== 'https:' || url.username || url.password || url.search || url.hash)
      return ''
    return url.href.replace(/\/$/, '')
  } catch {
    return ''
  }
}
