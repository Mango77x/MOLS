import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import ResetPasswordPage from './ResetPasswordPage'

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/reset-password" element={<ResetPasswordPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('ResetPasswordPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a missing-token message when the link has none', () => {
    renderAt('/reset-password')

    expect(screen.getByText(/missing its token/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/new password/i)).not.toBeInTheDocument()
  })

  it('redeems the token and shows success on a valid submit', async () => {
    const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: undefined })
    const user = userEvent.setup()

    renderAt('/reset-password?token=abc.def.ghi')

    await user.type(screen.getByLabelText(/new password/i), 'a-long-enough-password')
    await user.click(screen.getByRole('button', { name: /reset password/i }))

    expect(await screen.findByText(/has been reset/i)).toBeInTheDocument()
    expect(postSpy).toHaveBeenCalledWith('/auth/reset-password', {
      token: 'abc.def.ghi',
      newPassword: 'a-long-enough-password',
    })
  })

  it('shows the API error for an expired or already-used token', async () => {
    vi.spyOn(api, 'post').mockRejectedValue({
      response: { data: { message: 'This reset link is invalid or has expired.' } },
    })
    const user = userEvent.setup()

    renderAt('/reset-password?token=stale-token')

    await user.type(screen.getByLabelText(/new password/i), 'a-long-enough-password')
    await user.click(screen.getByRole('button', { name: /reset password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or has expired/i)
  })

  it('blocks submit with a validation error for a too-short password', async () => {
    const postSpy = vi.spyOn(api, 'post')
    const user = userEvent.setup()

    renderAt('/reset-password?token=abc.def.ghi')

    await user.type(screen.getByLabelText(/new password/i), 'short')
    await user.click(screen.getByRole('button', { name: /reset password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('12')
    expect(postSpy).not.toHaveBeenCalled()
  })
})
