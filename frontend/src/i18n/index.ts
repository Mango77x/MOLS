import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'
import en from './en.json'
import es from './es.json'
import fr from './fr.json'

/**
 * Supported UI languages. Adding one is "drop a JSON file + one entry
 * here" — nothing else in the app hardcodes this list (the language
 * switcher and useLocale both read it from here).
 */
export const SUPPORTED_LOCALES = ['en', 'es', 'fr'] as const
export type Locale = (typeof SUPPORTED_LOCALES)[number]

export const LOCALE_LABELS: Record<Locale, string> = {
  en: 'English',
  es: 'Español',
  fr: 'Français',
}

/**
 * Locales that read right-to-left. Empty today — none of the three
 * shipped languages need it — but `useLocale` already wires `dir` off
 * this set, so adding e.g. Arabic later doesn't require touching that
 * plumbing again.
 */
export const RTL_LOCALES: ReadonlySet<string> = new Set([])

// Locale detection/persistence is owned by useLocale (mirrors useTheme's
// pattern: localStorage, then a platform signal, then a fixed default) —
// deliberately not i18next-browser-languagedetector, to keep a single
// source of truth for "what locale is active" instead of two systems that
// could disagree.
void i18next.use(initReactI18next).init({
  resources: {
    en: { translation: en },
    es: { translation: es },
    fr: { translation: fr },
  },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
})

export default i18next
