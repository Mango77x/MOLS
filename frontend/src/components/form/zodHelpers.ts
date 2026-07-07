import { z } from 'zod'

/**
 * Numeric field helpers for react-hook-form + zod forms.
 *
 * These deliberately avoid `z.preprocess`: registering fields with
 * `{ valueAsNumber: true }` already gives the resolver real `number` values
 * (or `NaN` for an empty/untouched input), so the schema's input and output
 * types stay identical — `z.preprocess` widens the input type to `unknown`,
 * which breaks `useForm`'s generic inference under `tsc -b`'s stricter
 * project-reference checking (though not under a plain `tsc --noEmit`).
 */

/**
 * A required id from a `<select>` (e.g. a foreign key) — `NaN`/0/negative all
 * fail with `label`. Built with `z.custom` (see {@link coordinate}) so an
 * untouched/empty field shows `label` instead of zod's own generic
 * "expected number, received NaN".
 */
export const positiveId = (label: string) =>
  z.custom<number>((val) => typeof val === 'number' && !Number.isNaN(val) && val > 0, { message: label })

/** A required positive quantity/amount — `NaN`, 0 or negative all fail with `label`. */
export const positiveNumber = (label: string) =>
  z.custom<number>((val) => typeof val === 'number' && !Number.isNaN(val) && val > 0, { message: label })

/** A required quantity that may be zero (e.g. initial stock) — `NaN` or negative fail with `label`. */
export const nonNegativeNumber = (label: string) =>
  z.custom<number>((val) => typeof val === 'number' && !Number.isNaN(val) && val >= 0, { message: label })

/**
 * An optional numeric coordinate within [min, max]. `undefined` (untouched)
 * and `NaN` (field cleared by the user) both pass — only a real out-of-range
 * number fails.
 *
 * Built with `z.custom` rather than `z.number().optional()`: zod's built-in
 * number type rejects `NaN` at the type-check stage, before any `.refine()`
 * gets a chance to treat it as "not provided" — `z.custom` skips that
 * built-in check while still declaring the same `number | undefined` type on
 * both sides, so it doesn't hit the `tsc -b` resolver-generic issue that
 * `z.preprocess` does (see the note on the helpers above).
 */
export const coordinate = (min: number, max: number, label: string) =>
  z.custom<number | undefined>(
    (val) =>
      val === undefined ||
      (typeof val === 'number' && (Number.isNaN(val) || (val >= min && val <= max))),
    { message: `${label} must be between ${min} and ${max}` },
  )
