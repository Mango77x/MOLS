export { positiveId } from '../../../components/form/zodHelpers'

export interface DraftItem {
  resourceId: number
  resourceName: string
  quantity: number
}

export interface WizardHeader {
  unitId: number | undefined
  warehouseId: number | undefined
  dateCreated: string
  status: 'CREATED' | 'VALIDATED' | 'COMPLETED' | 'CANCELLED'
}

export interface WizardShipment {
  enabled: boolean
  vehicleId?: number
  status: 'PLANNED' | 'IN_TRANSIT' | 'DELIVERED'
  /** Draft items (matched back to the order's real items by resourceId once created) and how much of each to ship now. */
  items: { resourceId: number; quantity: number }[]
}
