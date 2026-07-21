import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import ConfirmDialog from './ConfirmDialog'

function setup(open: boolean, onConfirm = vi.fn(), onCancel = vi.fn()) {
  render(
    <ConfirmDialog
      open={open}
      title="Delete this thing?"
      message="This cannot be undone."
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
  )
  return { onConfirm, onCancel }
}

describe('ConfirmDialog', () => {
  it('renders nothing when closed', () => {
    setup(false)
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
  })

  it('renders the title and message when open', () => {
    setup(true)
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByText('Delete this thing?')).toBeInTheDocument()
    expect(screen.getByText('This cannot be undone.')).toBeInTheDocument()
  })

  it('focuses the confirm button on open, so Enter confirms without a mouse', () => {
    setup(true)
    expect(screen.getByRole('button', { name: 'Delete' })).toHaveFocus()
  })

  it('calls onConfirm when the confirm button is clicked', async () => {
    const user = userEvent.setup()
    const { onConfirm } = setup(true)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when the cancel button is clicked', async () => {
    const user = userEvent.setup()
    const { onCancel } = setup(true)

    await user.click(screen.getByRole('button', { name: 'Cancel' }))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel on Escape', async () => {
    const user = userEvent.setup()
    const { onCancel } = setup(true)

    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('calls onCancel when the backdrop is clicked', async () => {
    const user = userEvent.setup()
    const { onCancel } = setup(true)

    // The backdrop is the only element outside the dialog card that's part
    // of this component's own markup — query it via its aria-hidden marker.
    const backdrop = document.querySelector('[aria-hidden="true"]')
    expect(backdrop).not.toBeNull()
    await user.click(backdrop as Element)
    expect(onCancel).toHaveBeenCalledTimes(1)
  })
})
