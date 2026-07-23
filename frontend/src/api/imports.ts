/** Mirrors the REST API's Sprint 20 bulk-import DTOs (dto/request/CreateXRequest.java, dto/response/Import*.java). */

export interface CreateWarehouseRequest {
  name: string
  location: string
  latitude: number | null
  longitude: number | null
}

export interface CreateResourceRequest {
  name: string
  type: string
  criticality: string
}

export interface CreateUnitRequest {
  name: string
  location: string
  latitude: number | null
  longitude: number | null
}

export type ImportRowStatus = 'VALID' | 'DUPLICATE_WARNING' | 'ERROR'

export interface ImportRowResult<T> {
  rowNumber: number
  status: ImportRowStatus
  errors: string[]
  data: T | null
}

export interface ImportPreviewResponse<T> {
  rows: ImportRowResult<T>[]
  validCount: number
  duplicateWarningCount: number
  errorCount: number
}
