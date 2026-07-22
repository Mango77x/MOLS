import { useCallback, useEffect, useState } from 'react'
import i18n, { RTL_LOCALES, SUPPORTED_LOCALES, type Locale } from '../i18n'

const STORAGE_KEY = 'mols-locale'

function isSupportedLocale(value: string): value is Locale {
  return (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

function initialLocale(): Locale {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && isSupportedLocale(stored)) {
    return stored
  }
  // navigator.language is a full BCP-47 tag (e.g. "es-ES") — match on the
  // primary subtag so any regional variant of a supported language works.
  const browserPrimary = navigator.language.split('-')[0]
  if (isSupportedLocale(browserPrimary)) {
    return browserPrimary
  }
  return 'en'
}

/**
 * Active UI language, with persistence and `<html lang>`/`dir` — mirrors
 * `useTheme`'s shape exactly (localStorage, then a platform signal, then a
 * fixed default; a `useEffect` keeps the DOM in sync with state).
 */
export function useLocale() {
  const [locale, setLocaleState] = useState<Locale>(initialLocale)

  useEffect(() => {
    void i18n.changeLanguage(locale)
    document.documentElement.lang = locale
    document.documentElement.dir = RTL_LOCALES.has(locale) ? 'rtl' : 'ltr'
    localStorage.setItem(STORAGE_KEY, locale)
  }, [locale])

  const setLocale = useCallback((next: Locale) => setLocaleState(next), [])

  return { locale, setLocale }
}
