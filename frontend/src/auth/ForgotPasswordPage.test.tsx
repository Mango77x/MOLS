import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { api } from '../api/client'
import ForgotPasswordPage from './ForgotPasswordPage'

/**
 * The backend always answers 200 to forgot-password regardless of whether
 * the email matches an account, specifically to avoid account enumeration.
 * These tests pin that the frontend mirrors that: the same confirmation
 * shows up whether the API call "succeeds" (200) or errors out.
 */
describe('ForgotPasswordPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the generic confirmation after a successful submit', async () => {
    const postSpy = vi.spyOn(api, 'post').mockResolvedValue({ data: undefined })
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Email'), 'alice@example.com')
    await user.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(await screen.findByText(/if that email is on an account/i)).toBeInTheDocument()
    expect(postSpy).toHaveBeenCalledWith('/auth/forgot-password', { email: 'alice@example.com' })
  })

  it('shows the same generic confirmation even when the API call errors', async () => {
    vi.spyOn(api, 'post').mockRejectedValue(new Error('network error'))
    const user = userEvent.setup()

    render(
      <MemoryRouter>
        <ForgotPasswordPage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText('Email'), 'alice@example.com')
    await user.click(screen.getByRole('button', { name: /send reset link/i }))

    await waitFor(() =>
      expect(screen.getByText(/if that email is on an account/i)).toBeInTheDocument(),
    )
  })
})
