import { describe, expect, it } from 'vitest'
import { toCsv } from './csv'

describe('toCsv', () => {
  it('joins headers and rows with commas and CRLF', () => {
    expect(toCsv(['A', 'B'], [['1', '2'], ['3', '4']])).toBe('A,B\r\n1,2\r\n3,4')
  })

  it('quotes a field containing a comma', () => {
    expect(toCsv(['Name'], [['Acme, Inc.']])).toBe('Name\r\n"Acme, Inc."')
  })

  it('quotes a field containing a newline', () => {
    expect(toCsv(['Note'], [['line one\nline two']])).toBe('Note\r\n"line one\nline two"')
  })

  it('escapes and quotes a field containing a double quote', () => {
    expect(toCsv(['Quote'], [['She said "hi"']])).toBe('Quote\r\n"She said ""hi"""')
  })

  it('leaves plain fields unquoted', () => {
    expect(toCsv(['Value'], [['plain text']])).toBe('Value\r\nplain text')
  })

  it('handles an empty row set', () => {
    expect(toCsv(['A', 'B'], [])).toBe('A,B')
  })
})
