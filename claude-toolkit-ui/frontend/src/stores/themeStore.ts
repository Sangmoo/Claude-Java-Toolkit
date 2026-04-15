import { create } from 'zustand'

type Theme = 'dark' | 'light'

/**
 * v4.2.8 — 컬러 프리셋. data-theme(dark/light) 와 독립적으로 accent/bg 조합을
 * 바꾼다. 'default' 는 프리셋 없이 기본 dark/light 팔레트를 사용.
 *
 * CSS 는 [data-preset="dracula"] 같은 attribute selector 로 변수를 override 한다.
 * (claude-toolkit-ui/frontend/src/styles/theme.css 참조)
 */
export type ColorPreset = 'default' | 'dracula' | 'nord' | 'solarized-dark' | 'solarized-light' | 'github-light' | 'monokai'

export const COLOR_PRESETS: { id: ColorPreset; name: string; hint: string; suggestedTheme: Theme }[] = [
  { id: 'default',         name: '기본 (Claude Orange)',    hint: 'Dark + 오렌지 accent',  suggestedTheme: 'dark'  },
  { id: 'dracula',         name: 'Dracula',                 hint: 'Dark + 보라 accent',    suggestedTheme: 'dark'  },
  { id: 'nord',            name: 'Nord',                    hint: 'Dark + 한류 블루',      suggestedTheme: 'dark'  },
  { id: 'solarized-dark',  name: 'Solarized Dark',          hint: '따뜻한 다크 + 황색',    suggestedTheme: 'dark'  },
  { id: 'solarized-light', name: 'Solarized Light',         hint: '크림 + 황색',           suggestedTheme: 'light' },
  { id: 'github-light',    name: 'GitHub Light',            hint: '친숙한 라이트 + 파랑',  suggestedTheme: 'light' },
  { id: 'monokai',         name: 'Monokai',                 hint: 'Dark + 분홍 accent',    suggestedTheme: 'dark'  },
]

interface ThemeState {
  theme: Theme
  preset: ColorPreset
  toggleTheme: () => void
  setTheme: (theme: Theme) => void
  setPreset: (preset: ColorPreset) => void
}

const getInitialTheme = (): Theme => {
  const saved = localStorage.getItem('theme')
  if (saved === 'light' || saved === 'dark') return saved
  // OS 다크/라이트 모드 자동 감지
  if (typeof window !== 'undefined' && window.matchMedia) {
    return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
  }
  return 'dark'
}

const getInitialPreset = (): ColorPreset => {
  const saved = localStorage.getItem('theme-preset') as ColorPreset | null
  const valid: ColorPreset[] = ['default', 'dracula', 'nord', 'solarized-dark', 'solarized-light', 'github-light', 'monokai']
  return saved && valid.includes(saved) ? saved : 'default'
}

// 초기 DOM 반영 — 페이지 로드 직후 플래시 방지
if (typeof document !== 'undefined') {
  const initialTheme  = getInitialTheme()
  const initialPreset = getInitialPreset()
  document.documentElement.setAttribute('data-theme', initialTheme)
  if (initialPreset !== 'default') {
    document.documentElement.setAttribute('data-preset', initialPreset)
  }
}

export const useThemeStore = create<ThemeState>((set) => ({
  theme:  getInitialTheme(),
  preset: getInitialPreset(),

  toggleTheme: () =>
    set((state) => {
      const next: Theme = state.theme === 'dark' ? 'light' : 'dark'
      localStorage.setItem('theme', next)
      document.documentElement.setAttribute('data-theme', next)
      return { theme: next }
    }),

  setTheme: (theme: Theme) => {
    localStorage.setItem('theme', theme)
    document.documentElement.setAttribute('data-theme', theme)
    set({ theme })
  },

  setPreset: (preset: ColorPreset) => {
    localStorage.setItem('theme-preset', preset)
    if (preset === 'default') {
      document.documentElement.removeAttribute('data-preset')
    } else {
      document.documentElement.setAttribute('data-preset', preset)
    }
    set({ preset })
  },
}))
