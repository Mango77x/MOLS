import { Link, useLocation } from 'react-router-dom'

/**
 * Catch-all for any /app/* path that doesn't match a real route.
 *
 * Before this existed, an unmatched path (e.g. /app/stock — singular, the
 * exact URL a user would guess from the sidebar's "Stock" label, whose real
 * route is /app/stocks) rendered a completely blank page with no error, no
 * navigation, nothing to click — see the nav label vs. route table below.
 */
export default function NotFoundPage() {
  const location = useLocation()

  return (
    <div>
      <h1 className="mb-2 text-xl font-bold">Page not found</h1>
      <p className="text-sm text-gray-600 dark:text-gray-300">
        There's no page at <code className="rounded bg-gray-100 px-1 py-0.5 dark:bg-gray-800">{location.pathname}</code>.
      </p>
      <Link to="/" className="mt-4 inline-block text-sm font-medium text-army-700 underline dark:text-army-300">
        Back to dashboard
      </Link>
    </div>
  )
}
