import { onMounted, onUnmounted } from 'vue'

export const useEscapeKey = (isActive: () => boolean, callback: () => void) => {
  const handleKeydown = (event: KeyboardEvent) => {
    if (event.key === 'Escape' && isActive()) callback()
  }

  onMounted(() => window.addEventListener('keydown', handleKeydown))
  onUnmounted(() => window.removeEventListener('keydown', handleKeydown))
}
