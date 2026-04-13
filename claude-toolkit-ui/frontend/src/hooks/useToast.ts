import { useToastStore, type ToastType } from '../stores/toastStore'

export function useToast() {
  const show = useToastStore((s) => s.show)

  return {
    success: (msg: string) => show(msg, 'success'),
    error: (msg: string) => show(msg, 'error'),
    warning: (msg: string) => show(msg, 'warning'),
    info: (msg: string) => show(msg, 'info'),
    show: (msg: string, type?: ToastType, duration?: number) =>
      show(msg, type, duration),
  }
}
