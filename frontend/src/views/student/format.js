// Missing or withheld values must not become zero; zero itself is a valid recorded value.
export const formatGpa = (value) => {
  if (value == null || value === '') return '—'
  const number = Number(value)
  return Number.isFinite(number) ? number.toFixed(2) : '—'
}
