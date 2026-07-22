/**
 * Minimal RFC 4180-ish CSV writer: quotes a field only when it contains a
 * comma, quote, or newline, and doubles up embedded quotes. Good enough for
 * the app's own data (names, dates, numbers) without pulling in a library.
 */
export function toCsv(headers: string[], rows: string[][]): string {
  function escapeField(value: string): string {
    if (/[",\r\n]/.test(value)) {
      return `"${value.replace(/"/g, '""')}"`
    }
    return value
  }
  return [headers, ...rows].map((row) => row.map(escapeField).join(',')).join('\r\n')
}

/**
 * Triggers a browser download of `csv` as `filename`. The UTF-8 BOM is
 * required for Excel (unlike most CSV consumers) to detect the encoding
 * correctly instead of mangling accented ES/FR characters.
 */
export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
