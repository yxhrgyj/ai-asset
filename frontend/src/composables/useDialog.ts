import { ref } from 'vue'

interface DialogOptions {
  title?: string
  message: string
  type?: 'info' | 'warning' | 'error' | 'success'
  confirmText?: string
  cancelText?: string
  showCancel?: boolean
}

interface DialogState extends DialogOptions {
  visible: boolean
  resolve?: (value: boolean) => void
}

const dialogState = ref<DialogState>({
  visible: false,
  message: '',
  type: 'info',
  confirmText: '确认',
  cancelText: '取消',
  showCancel: false
})

export function useDialog() {
  const confirm = (options: string | DialogOptions): Promise<boolean> => {
    return new Promise((resolve) => {
      const opts = typeof options === 'string'
        ? { message: options, showCancel: true }
        : { ...options, showCancel: true }

      dialogState.value = {
        visible: true,
        title: opts.title || '确认操作',
        message: opts.message,
        type: opts.type || 'warning',
        confirmText: opts.confirmText || '确认',
        cancelText: opts.cancelText || '取消',
        showCancel: true,
        resolve
      }
    })
  }

  const alert = (options: string | DialogOptions): Promise<boolean> => {
    return new Promise((resolve) => {
      const opts = typeof options === 'string'
        ? { message: options }
        : options

      dialogState.value = {
        visible: true,
        title: opts.title || '提示',
        message: opts.message,
        type: opts.type || 'info',
        confirmText: opts.confirmText || '知道了',
        showCancel: false,
        resolve
      }
    })
  }

  const handleConfirm = () => {
    dialogState.value.resolve?.(true)
    dialogState.value.visible = false
  }

  const handleCancel = () => {
    dialogState.value.resolve?.(false)
    dialogState.value.visible = false
  }

  return {
    dialogState,
    confirm,
    alert,
    handleConfirm,
    handleCancel
  }
}
