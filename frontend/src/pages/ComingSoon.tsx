import { Link } from 'react-router-dom'

/**
 * Placeholder for sections that have not been migrated from the Thymeleaf UI
 * yet. Each one links back to its working /ui counterpart so nothing is lost
 * during the incremental migration.
 */
export default function ComingSoon({ title, uiPath }: { title: string; uiPath: string }) {
  return (
    <div>
      <h1 className="mb-2 text-xl font-bold">{title}</h1>
      <p className="text-sm text-gray-600 dark:text-gray-300">
        This section is being migrated to the new interface. Meanwhile, it is
        fully available in the{' '}
        <a
          href={uiPath}
          className="font-medium text-army-700 underline dark:text-army-300"
        >
          current admin UI
        </a>
        .
      </p>
      <Link
        to="/"
        className="mt-4 inline-block text-sm font-medium text-army-700 underline dark:text-army-300"
      >
        Back to dashboard
      </Link>
    </div>
  )
}
