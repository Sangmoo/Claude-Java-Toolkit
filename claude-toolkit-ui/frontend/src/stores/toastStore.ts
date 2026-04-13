import { create } from 'zustand'

export type ToastType = 'success' | 'error' | 'warning' | 'info'

interface Toast {
  id: number
  message: string
  type: ToastType
  removing?: boolean
}

interface ToastState {
  toasts: Toast[]
  show: (message: string, type?: ToastType, duration?: number) => void
  remove: (id: number) => void
}

let nextId = 0

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],

  show: (message, type = 'info', duration = 4000) => {
    const id = ++nextId
    set((s) => ({
      toasts: [...s.toasts.slice(-4), { id, message, type }],
    }))
    setTimeout(() => {
      set((s) => ({
        toasts: s.toasts.map((t) =>
          t.id === id ? { ...t, removing: true } : t
        ),
      }))
      setTimeout(() => {
        set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
      }, 300)
    }, duration)
  },

  remove: (id) => {
    set((s) => ({
      toasts: s.toasts.map((t) =>
        t.id === id ? { ...t, removing: true } : t
      ),
    }))
    setTimeout(() => {
      set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) }))
    }, 300)
  },
}))
