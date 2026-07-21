import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi, afterEach } from 'vitest'
import { api } from '../../api/client'
import UserFormPage from './UserFormPage'

/**
 * Regression coverage for the Sprint 10 SelectField/defaultValue bug: a
 * plain <select> with no `defaultValue` auto-selects its first non-disabled
 * <option> on mount, so leaving the Role field untouched used to submit
 * silently as ADMIN instead of being rejected as "required". This is the
 * most severe instance of that bug class — accidentally creating an admin
 * account — so it gets its own dedicated test rather than just relying on
 * the shared enumLabels/SelectField pattern being right elsewhere.
 */
describe('UserFormPage — role select', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows "Select a role" as the initial selection, not the first real option', () => {
    render(
      <MemoryRouter>
        <UserFormPage />
      </MemoryRouter>,
    )

    const roleSelect = screen.getByLabelText('Role') as HTMLSelectElement
    expect(roleSelect.value).toBe('')
  })

  it('blocks submit and shows a validation error when Role is left untouched', async () => {
    const postSpy = vi.spyOn(api, 'post')
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <UserFormPage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Username'), 'newoperator')
    await user.type(screen.getByLabelText('Password'), 'a-long-enough-password')
    await user.click(screen.getByRole('button', { name: /save/i }))

    // "Select a role" also appears as the disabled placeholder <option>'s
    // text, so target the error message specifically via its alert role.
    expect(await screen.findByRole('alert')).toHaveTextContent('Select a role')
    expect(postSpy).not.toHaveBeenCalled()
  })

  it('submits the chosen role once one is actually selected', async () => {
    const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: {} })
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <UserFormPage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Username'), 'newoperator')
    await user.type(screen.getByLabelText('Password'), 'a-long-enough-password')
    await user.selectOptions(screen.getByLabelText('Role'), 'OPERATOR')
    await user.click(screen.getByRole('button', { name: /save/i }))

    await waitFor(() =>
      expect(postSpy).toHaveBeenCalledWith('/users', {
        username: 'newoperator',
        password: 'a-long-enough-password',
        role: 'OPERATOR',
      }),
    )
  })
})
