import { afterEach, describe, expect, it } from 'vitest'
import i18n from '../i18n'
import {
  enumLabel,
  ORDER_STATUS_LABELS,
  ROLE_LABELS,
  SHIPMENT_STATUS_LABELS,
  VEHICLE_STATUS_LABELS,
  VEHICLE_TYPE_LABELS,
} from './enumLabels'

describe('enumLabel', () => {
  afterEach(() => {
    void i18n.changeLanguage('en')
  })

  it('returns the mapped friendly label for a known value', () => {
    expect(enumLabel(ORDER_STATUS_LABELS, 'PARTIALLY_SHIPPED')).toBe('Partially shipped')
  })

  it('falls back to the raw value for an unmapped value', () => {
    expect(enumLabel(ORDER_STATUS_LABELS, 'SOME_FUTURE_STATUS')).toBe('SOME_FUTURE_STATUS')
  })

  it('resolves through the active i18next language, not a hardcoded one', async () => {
    await i18n.changeLanguage('es')
    expect(enumLabel(ORDER_STATUS_LABELS, 'PARTIALLY_SHIPPED')).toBe('Parcialmente enviado')
  })
})

// Every value the backend can actually send for each enum (see the
// corresponding zod schemas: OrderEditFormPage/HeaderStep, ShipmentFormPage,
// VehicleFormPage). If one of these lists ever grows, this test starts
// failing instead of quietly falling back to the raw value (still correct,
// but the whole point of this module is to not show a raw enum value).
describe('label map completeness', () => {
  it('covers every OrderStatus value', () => {
    expect(Object.keys(ORDER_STATUS_LABELS).sort()).toEqual(
      ['CANCELLED', 'COMPLETED', 'CREATED', 'PARTIALLY_SHIPPED', 'VALIDATED'].sort(),
    )
  })

  it('covers every ShipmentStatus value', () => {
    expect(Object.keys(SHIPMENT_STATUS_LABELS).sort()).toEqual(['DELIVERED', 'IN_TRANSIT', 'PLANNED'].sort())
  })

  it('covers every VehicleStatus value', () => {
    expect(Object.keys(VEHICLE_STATUS_LABELS).sort()).toEqual(['AVAILABLE', 'IN_REPAIR', 'IN_USE'].sort())
  })

  it('covers every VehicleType value', () => {
    expect(Object.keys(VEHICLE_TYPE_LABELS).sort()).toEqual(['AIR', 'LAND', 'SEA'].sort())
  })

  it('covers every Role value', () => {
    expect(Object.keys(ROLE_LABELS).sort()).toEqual(['ADMIN', 'AUDITOR', 'OPERATOR'].sort())
  })

  it('never resolves a value to an empty or unchanged-but-ugly label', () => {
    const allMaps = [
      ORDER_STATUS_LABELS,
      SHIPMENT_STATUS_LABELS,
      VEHICLE_STATUS_LABELS,
      VEHICLE_TYPE_LABELS,
      ROLE_LABELS,
    ]
    for (const map of allMaps) {
      for (const value of Object.keys(map)) {
        const label = enumLabel(map, value)
        expect(label.length).toBeGreaterThan(0)
        // A friendly label should read as a sentence fragment, not
        // SHOUT_CASE — this is what the whole module exists to fix.
        expect(label).not.toBe(value)
        expect(label).not.toMatch(/_/)
      }
    }
  })

  it('every map entry is a resolvable i18next key, not leftover literal text', () => {
    const allMaps = [
      ORDER_STATUS_LABELS,
      SHIPMENT_STATUS_LABELS,
      VEHICLE_STATUS_LABELS,
      VEHICLE_TYPE_LABELS,
      ROLE_LABELS,
    ]
    for (const map of allMaps) {
      for (const key of Object.values(map)) {
        expect(i18n.exists(key)).toBe(true)
      }
    }
  })
})
