import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { useLocale } from './useLocale'

function setBrowserLanguage(value: string) {
  Object.defineProperty(window.navigator, 'language', { value, configurable: true })
}

describe('useLocale', () => {
  beforeEach(() => {
    localStorage.clear()
    setBrowserLanguage('en-US')
  })

  afterEach(() => {
    localStorage.clear()
    setBrowserLanguage('en-US')
  })

  it('defaults to a supported locale matched from the browser language when nothing is stored', () => {
    setBrowserLanguage('es-ES')
    const { result } = renderHook(() => useLocale())
    expect(result.current.locale).toBe('es')
  })

  it('falls back to English when the browser language is not supported', () => {
    setBrowserLanguage('de-DE')
    const { result } = renderHook(() => useLocale())
    expect(result.current.locale).toBe('en')
  })

  it('reads a previously persisted locale over the browser language', () => {
    localStorage.setItem('mols-locale', 'fr')
    setBrowserLanguage('es-ES')
    const { result } = renderHook(() => useLocale())
    expect(result.current.locale).toBe('fr')
  })

  it('persists the locale, sets <html lang>, and keeps dir="ltr" for the three shipped languages', () => {
    const { result } = renderHook(() => useLocale())

    act(() => result.current.setLocale('fr'))

    expect(result.current.locale).toBe('fr')
    expect(localStorage.getItem('mols-locale')).toBe('fr')
    expect(document.documentElement.lang).toBe('fr')
    expect(document.documentElement.dir).toBe('ltr')
  })
})
