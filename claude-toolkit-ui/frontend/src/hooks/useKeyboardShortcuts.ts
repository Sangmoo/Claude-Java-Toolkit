import { useEffect, useState } from 'react'

export function useKeyboardShortcuts() {
  const [showHelp, setShowHelp] = useState(false)

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      const target = e.target as HTMLElement
      const isInput = target.tagName === 'INPUT' || target.tagName === 'TEXTAREA' || target.tagName === 'SELECT'

      // ? — 단축키 도움말 토글 (입력 필드 제외)
      if (e.key === '?' && !isInput) {
        e.preventDefault()
        setShowHelp((prev) => !prev)
        return
      }

      // Esc — 도움말 닫기
      if (e.key === 'Escape') {
        setShowHelp(false)
        return
      }

      // Ctrl+Enter / ⌘+Enter — 폼 제출
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter' && isInput) {
        const form = target.closest('form')
        const submitBtn = form?.querySelector('button[type="submit"]') as HTMLButtonElement
        if (submitBtn && !submitBtn.disabled) {
          submitBtn.click()
        }
      }
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])

  return { showHelp, closeHelp: () => setShowHelp(false) }
}
