export { positiveId } from '../../../components/form/zodHelpers'

export interface DraftItem {
  resourceId: number
  resourceName: string
  quantity: number
}

export interface WizardHeader {
  unitId: number | undefined
  dateCreated: string
  status: 'CREATED' | 'VALIDATED' | 'COMPLETED' | 'CANCELLED'
}

export interface WizardShipment {
  enabled: boolean
  vehicleId?: number
  warehouseId?: number
  status: 'PLANNED' | 'IN_TRANSIT' | 'DELIVERED'
}
