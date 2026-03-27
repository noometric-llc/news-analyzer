'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface EvaluationSidebarState {
  // State
  isCollapsed: boolean;
  isMobileOpen: boolean;

  // Actions
  toggle: () => void;
  collapse: () => void;
  expand: () => void;
  toggleMobile: () => void;
  closeMobile: () => void;
}

export const useEvaluationSidebarStore = create<EvaluationSidebarState>()(
  persist(
    (set) => ({
      // Initial state - start expanded on desktop
      isCollapsed: false,
      isMobileOpen: false,

      // Toggle collapsed state
      toggle: () => set((state) => ({ isCollapsed: !state.isCollapsed })),

      // Collapse sidebar
      collapse: () => set({ isCollapsed: true }),

      // Expand sidebar
      expand: () => set({ isCollapsed: false }),

      // Toggle mobile sidebar
      toggleMobile: () => set((state) => ({ isMobileOpen: !state.isMobileOpen })),

      // Close mobile sidebar (used when navigating or clicking backdrop)
      closeMobile: () => set({ isMobileOpen: false }),
    }),
    {
      name: 'eval-sidebar-storage',
      partialize: (state) => ({ isCollapsed: state.isCollapsed }),
    }
  )
);
