import { beforeEach, describe, expect, it } from 'vitest'
import { useAuthStore } from './store'

describe('auth store', () => {
  beforeEach(() => {
    useAuthStore.setState({ status: 'unknown', user: null })
  })

  it('starts with an unknown session', () => {
    expect(useAuthStore.getState().status).toBe('unknown')
    expect(useAuthStore.getState().user).toBeNull()
  })

  it('setSession marks the user as authenticated', () => {
    useAuthStore.getState().setSession({ username: 'ops', role: 'OPERATOR' })

    const state = useAuthStore.getState()
    expect(state.status).toBe('authenticated')
    expect(state.user).toEqual({ username: 'ops', role: 'OPERATOR' })
  })

  it('clearSession drops to anonymous (used on logout and 401s)', () => {
    useAuthStore.getState().setSession({ username: 'ops', role: 'OPERATOR' })
    useAuthStore.getState().clearSession()

    const state = useAuthStore.getState()
    expect(state.status).toBe('anonymous')
    expect(state.user).toBeNull()
  })
})
