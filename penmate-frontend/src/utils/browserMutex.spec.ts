import { beforeEach, describe, expect, it, vi } from 'vitest'
import { withLocalStorageMutex } from './browserMutex'

describe('withLocalStorageMutex', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.useRealTimers()
  })

  it('serializes contenders and removes its lock after completion', async () => {
    let activeTasks = 0
    let maxActiveTasks = 0
    let completedTasks = 0
    const enter = () => {
      activeTasks += 1
      maxActiveTasks = Math.max(maxActiveTasks, activeTasks)
    }
    const leave = () => {
      activeTasks -= 1
      completedTasks += 1
    }
    const first = withLocalStorageMutex('test.lock', async () => {
      enter()
      await new Promise((resolve) => window.setTimeout(resolve, 20))
      leave()
    }, { retryMinMs: 1, retryMaxMs: 1 })
    const second = withLocalStorageMutex('test.lock', async () => {
      enter()
      leave()
    }, { retryMinMs: 1, retryMaxMs: 1 })

    await Promise.all([first, second])

    expect(maxActiveTasks).toBe(1)
    expect(completedTasks).toBe(2)
    expect(window.localStorage.getItem('test.lock')).toBeNull()
  })

  it('reclaims an expired lock', async () => {
    window.localStorage.setItem('test.lock', JSON.stringify({ owner: 'stale', expiresAt: 99 }))
    const task = vi.fn().mockResolvedValue('done')

    await expect(withLocalStorageMutex('test.lock', task, {
      now: () => 100,
      retryMinMs: 1,
      retryMaxMs: 1,
    })).resolves.toBe('done')

    expect(task).toHaveBeenCalledOnce()
    expect(window.localStorage.getItem('test.lock')).toBeNull()
  })
})
