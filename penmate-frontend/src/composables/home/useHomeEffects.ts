import { onMounted, onUnmounted, ref } from 'vue'

export const useHomeEffects = () => {
  const isScrolled = ref(window.scrollY > 60)

  const particleStyles = Array.from({ length: 20 }, () => {
    const size = Math.random() * 4 + 1
    const left = Math.random() * 100
    const duration = Math.random() * 15 + 15
    const delay = Math.random() * 20
    const opacity = Math.random() * 0.5 + 0.1

    return {
      width: `${size}px`,
      height: `${size}px`,
      left: `${left}%`,
      bottom: '-10px',
      animationDuration: `${duration}s`,
      animationDelay: `${delay}s`,
      opacity,
    }
  })

  const handleScroll = () => {
    isScrolled.value = window.scrollY > 60
  }

  const particleStyle = (index: number) => particleStyles[index - 1] ?? particleStyles[0]

  onMounted(() => {
    handleScroll()
    window.addEventListener('scroll', handleScroll)
  })

  onUnmounted(() => {
    window.removeEventListener('scroll', handleScroll)
  })

  return {
    isScrolled,
    particleStyle,
  }
}
