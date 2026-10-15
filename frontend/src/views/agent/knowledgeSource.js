export const knowledgeSourceUrl = (chunk) => {
  if (typeof chunk?.source_url !== 'string' || chunk.source_url.length > 2048) return ''
  try {
    const url = new URL(chunk.source_url)
    return ['https:', 'http:'].includes(url.protocol) && !url.username && !url.password
      ? url.href
      : ''
  } catch {
    return ''
  }
}
export const knowledgeSourceLabel = (chunk) =>
  typeof chunk?.source === 'string' && chunk.source.trim() ? chunk.source : '出处未记录'
