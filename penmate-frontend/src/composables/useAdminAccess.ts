import { ref } from 'vue'
import { rbacApi } from '@/api/modules/rbac.api'
import { getSession } from '@/stores/session'

export const useAdminAccess = () => {
  const canAccessAdmin = ref(false)

  const loadAdminAccess = async () => {
    const userId = getSession().userId
    if (!userId) {
      canAccessAdmin.value = false
      return false
    }

    try {
      const menus = await rbacApi.listProfileMenus(userId)
      canAccessAdmin.value = (menus || []).some((menu) =>
        String(menu.path || '').startsWith('/admin'),
      )
    } catch {
      canAccessAdmin.value = false
    }
    return canAccessAdmin.value
  }

  return { canAccessAdmin, loadAdminAccess }
}
