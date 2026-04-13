import { create } from 'zustand'

interface SidebarState {
  collapsed: boolean
  mobileOpen: boolean
  sections: Record<string, boolean>
  toggleCollapse: () => void
  setCollapsed: (v: boolean) => void
  toggleMobile: () => void
  closeMobile: () => void
  toggleSection: (key: string) => void
}

const loadCollapsed = (): boolean =>
  localStorage.getItem('sidebar_collapsed_v1') === 'true'

const loadSections = (): Record<string, boolean> => {
  try {
    const raw = localStorage.getItem('sidebar_sections_v1')
    return raw ? JSON.parse(raw) : {}
  } catch {
    return {}
  }
}

export const useSidebarStore = create<SidebarState>((set) => ({
  collapsed: loadCollapsed(),
  mobileOpen: false,
  sections: loadSections(),

  toggleCollapse: () =>
    set((s) => {
      const next = !s.collapsed
      localStorage.setItem('sidebar_collapsed_v1', String(next))
      return { collapsed: next }
    }),

  setCollapsed: (v: boolean) => {
    localStorage.setItem('sidebar_collapsed_v1', String(v))
    set({ collapsed: v })
  },

  toggleMobile: () => set((s) => ({ mobileOpen: !s.mobileOpen })),
  closeMobile: () => set({ mobileOpen: false }),

  toggleSection: (key: string) =>
    set((s) => {
      const next = { ...s.sections, [key]: !s.sections[key] }
      localStorage.setItem('sidebar_sections_v1', JSON.stringify(next))
      return { sections: next }
    }),
}))
