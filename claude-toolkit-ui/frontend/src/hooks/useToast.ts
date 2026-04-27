import { useMemo } from 'react'
import { useToastStore, type ToastType } from '../stores/toastStore'

/**
 * v4.5 — 안정 참조 반환.
 * 이전 구현은 매 렌더마다 새 객체 리터럴을 반환하여 useEffect/useCallback 의
 * 의존 배열에 toast 를 넣으면 무한 재호출이 발생했음 (예: ProjectMapPage).
 * useMemo 로 래핑하여 show 함수 참조가 동일한 한 같은 객체를 반환.
 */
export function useToast() {
  const show = useToastStore((s) => s.show)

  return useMemo(() => ({
    success: (msg: string) => show(msg, 'success'),
    error:   (msg: string) => show(msg, 'error'),
    warning: (msg: string) => show(msg, 'warning'),
    info:    (msg: string) => show(msg, 'info'),
    show:    (msg: string, type?: ToastType, duration?: number) =>
      show(msg, type, duration),
  }), [show])
}
