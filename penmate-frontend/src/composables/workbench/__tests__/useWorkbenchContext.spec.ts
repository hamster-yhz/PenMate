import { afterEach, describe, expect, it } from 'vitest'

import { LAST_OPERATOR_ID_KEY, LAST_PROJECT_ID_KEY, useWorkbenchContext } from '../useWorkbenchContext'

describe('useWorkbenchContext', () => {
  afterEach(() => {
    window.localStorage.clear()
  })

  it('should_prioritize_query_over_session_and_local_storage_when_resolving_context', () => {
    window.localStorage.setItem(LAST_PROJECT_ID_KEY, 'project-701')
    window.localStorage.setItem(LAST_OPERATOR_ID_KEY, 'operator-801')

    const { projectId, operatorId } = useWorkbenchContext({
      query: {
        projectId: '101',
        operatorId: 'operator-201',
      },
      session: {
        userId: 'operator-301' as never,
      },
    })

    expect(projectId).toBe('101')
    expect(operatorId).toBe('operator-201')
  })

  it('should_fallback_to_session_before_local_storage_for_operator_id', () => {
    window.localStorage.setItem(LAST_PROJECT_ID_KEY, 'project-702')
    window.localStorage.setItem(LAST_OPERATOR_ID_KEY, 'operator-802')

    const { projectId, operatorId } = useWorkbenchContext({
      query: {
        projectId: '102',
      },
      session: {
        userId: 'operator-302' as never,
      },
    })

    expect(projectId).toBe('102')
    expect(operatorId).toBe('operator-302')
  })

  it('should_fallback_to_local_storage_when_query_and_session_are_missing', () => {
    window.localStorage.setItem(LAST_PROJECT_ID_KEY, 'project-703')
    window.localStorage.setItem(LAST_OPERATOR_ID_KEY, 'operator-803')

    const { projectId, operatorId } = useWorkbenchContext({
      query: {},
      session: {},
    })

    expect(projectId).toBe('project-703')
    expect(operatorId).toBe('operator-803')
  })

  it('should_persist_resolved_context_to_local_storage', () => {
    const { ensureContext } = useWorkbenchContext({
      query: {
        projectId: '104',
      },
      session: {
        userId: 'operator-304' as never,
      },
    })

    ensureContext()

    expect(window.localStorage.getItem(LAST_PROJECT_ID_KEY)).toBe('104')
    expect(window.localStorage.getItem(LAST_OPERATOR_ID_KEY)).toBe('operator-304')
  })

  it('should_expose_context_profile_and_ensure_context', () => {
    const { projectId, operatorId, username, userEmail, ensureContext } = useWorkbenchContext({
      query: {
        projectId: '105',
      },
      session: {
        userId: 'operator-305' as never,
        userName: '墨客',
        userEmail: 'moke@penmate.com',
      },
    })

    expect(projectId).toBe('105')
    expect(operatorId).toBe('operator-305')
    expect(username).toBe('墨客')
    expect(userEmail).toBe('moke@penmate.com')
    expect(ensureContext()).toEqual({
      projectId: '105',
      operatorId: 'operator-305',
      username: '墨客',
      userEmail: 'moke@penmate.com',
    })
  })

  it('should_treat_business_ids_as_trimmed_strings_without_user_id_query_fallback', () => {
    const { projectId, operatorId } = useWorkbenchContext({
      query: {
        projectId: '  project-alpha  ',
        userId: 'legacy-user-query',
      },
      session: {
        userId: 'operator-session-1' as never,
      },
    })

    expect(projectId).toBe('project-alpha')
    expect(operatorId).toBe('operator-session-1')
  })

  it('should_only_expose_plan_defined_public_contract', () => {
    const context = useWorkbenchContext({
      query: {
        projectId: '106',
        operatorId: 'operator-206',
      },
      session: {
        userId: 'operator-306' as never,
        userName: '执笔人',
        userEmail: 'writer@penmate.com',
      },
    })

    expect(Object.keys(context).sort()).toEqual(['ensureContext', 'operatorId', 'projectId', 'userEmail', 'username'])
  })
})
