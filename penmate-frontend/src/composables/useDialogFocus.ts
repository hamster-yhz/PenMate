import { nextTick, onBeforeUnmount, watch, type Ref } from 'vue'

const FOCUSABLE_SELECTOR = [
  '[data-dialog-initial-focus]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'a[href]',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

interface DialogFocusOptions {
  open: () => boolean
  dialog: Ref<HTMLElement | null>
  close: () => void
  canClose?: () => boolean
}

const focusableElements = (dialog: HTMLElement) =>
  Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
    .filter((element) => !element.hidden && element.getAttribute('aria-hidden') !== 'true')

export const useDialogFocus = ({ open, dialog, close, canClose = () => true }: DialogFocusOptions) => {
  let returnFocusTo: HTMLElement | null = null

  const focusDialog = async () => {
    await nextTick()
    const target = dialog.value
    if (!target) return
    const initialTarget = target.querySelector<HTMLElement>('[data-dialog-initial-focus]')
    ;(initialTarget || focusableElements(target)[0] || target).focus()
  }

  const restoreFocus = () => {
    const target = returnFocusTo
    returnFocusTo = null
    if (!target?.isConnected) return
    requestAnimationFrame(() => target.focus())
  }

  const handleKeydown = (event: KeyboardEvent) => {
    if (!open()) return
    if (event.key === 'Escape' && canClose()) {
      event.preventDefault()
      event.stopPropagation()
      close()
      return
    }
    if (event.key !== 'Tab' || !dialog.value) return

    const targets = focusableElements(dialog.value)
    if (!targets.length) {
      event.preventDefault()
      dialog.value.focus()
      return
    }

    const first = targets[0]
    const last = targets[targets.length - 1]
    const active = document.activeElement
    if (event.shiftKey && (active === first || !dialog.value.contains(active))) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && (active === last || !dialog.value.contains(active))) {
      event.preventDefault()
      first.focus()
    }
  }

  const stopWatching = watch(open, (isOpen, wasOpen) => {
    if (isOpen) {
      if (!wasOpen) returnFocusTo = document.activeElement instanceof HTMLElement ? document.activeElement : null
      document.addEventListener('keydown', handleKeydown)
      void focusDialog()
    } else {
      document.removeEventListener('keydown', handleKeydown)
      if (wasOpen) restoreFocus()
    }
  }, { immediate: true, flush: 'post' })

  onBeforeUnmount(() => {
    stopWatching()
    document.removeEventListener('keydown', handleKeydown)
    if (open()) restoreFocus()
  })
}
