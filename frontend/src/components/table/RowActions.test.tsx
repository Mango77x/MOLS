import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ToastProvider } from '../toast/ToastProvider'
import { DeleteAction } from './RowActions'

function renderDeleteAction(onConfirm: () => Promise<void> | void) {
  return render(
    <ToastProvider>
      <DeleteAction label="widget" onConfirm={onConfirm} />
    </ToastProvider>,
  )
}

describe('DeleteAction', () => {
  it('does not call onConfirm until the dialog is confirmed', async () => {
    const onConfirm = vi.fn()
    const user = userEvent.setup()
    renderDeleteAction(onConfirm)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('cancelling the dialog never calls onConfirm', async () => {
    const onConfirm = vi.fn()
    const user = userEvent.setup()
    renderDeleteAction(onConfirm)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(await screen.findByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('confirming calls onConfirm and shows a success toast', async () => {
    const onConfirm = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderDeleteAction(onConfirm)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    await waitFor(() => expect(onConfirm).toHaveBeenCalledTimes(1))
    expect(await screen.findByText('Widget deleted.')).toBeInTheDocument()
  })

  /**
   * Regression coverage: a failed delete (e.g. the API rejecting it because
   * dependent records exist) used to leave the row exactly as if the delete
   * had succeeded — no error, no toast, nothing. RowActions now surfaces the
   * API's message via a toast; this pins that behavior so it can't quietly
   * regress back to silent again.
   */
  it('a rejected onConfirm surfaces the error as a toast instead of failing silently', async () => {
    const onConfirm = vi.fn().mockRejectedValue({
      response: { data: { message: 'Cannot delete widget with existing dependents.' } },
    })
    const user = userEvent.setup()
    renderDeleteAction(onConfirm)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    const dialog = await screen.findByRole('alertdialog')
    await user.click(within(dialog).getByRole('button', { name: 'Delete' }))

    expect(await screen.findByText('Cannot delete widget with existing dependents.')).toBeInTheDocument()
  })
})
