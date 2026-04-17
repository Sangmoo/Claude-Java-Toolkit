import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import ko from './ko'
import en from './en'
import ja from './ja'
import zh from './zh'
import de from './de'

const SUPPORTED = ['ko', 'en', 'ja', 'zh', 'de'] as const
export type SupportedLang = typeof SUPPORTED[number]

const savedLang = localStorage.getItem('language') || 'ko'
const initialLang: SupportedLang = (SUPPORTED as readonly string[]).includes(savedLang)
  ? (savedLang as SupportedLang)
  : 'ko'

i18n.use(initReactI18next).init({
  resources: { ko, en, ja, zh, de },
  lng: initialLang,
  fallbackLng: 'ko',
  interpolation: { escapeValue: false },
})

/** v4.3.0: 언어 옵션 메타 — 선택 UI 에서 사용 */
export const LANGUAGE_OPTIONS: { code: SupportedLang; label: string; flag: string }[] = [
  { code: 'ko', label: '한국어',     flag: '🇰🇷' },
  { code: 'en', label: 'English',    flag: '🇺🇸' },
  { code: 'ja', label: '日本語',      flag: '🇯🇵' },
  { code: 'zh', label: '简体中文',    flag: '🇨🇳' },
  { code: 'de', label: 'Deutsch',    flag: '🇩🇪' },
]

export default i18n
