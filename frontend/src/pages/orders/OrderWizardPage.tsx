import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import { extractApiError } from '../../api/errors'
import { FormBanner } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import HeaderStep from './wizard/HeaderStep'
import ItemsStep from './wizard/ItemsStep'
import ShipmentStep from './wizard/ShipmentStep'
import type { DraftItem, WizardHeader, WizardShipment } from './wizard/shared'

const STEPS = ['Header', 'Items', 'Shipment'] as const

function Stepper({ current }: { current: 1 | 2 | 3 }) {
  return (
    <div className="mb-4 flex gap-4 text-sm">
      {STEPS.map((label, index) => {
        const stepNumber = (index + 1) as 1 | 2 | 3
        const active = stepNumber === current
        const done = stepNumber < current
        return (
          <div
            key={label}
            className={
              active
                ? 'font-semibold text-army-700 dark:text-army-300'
                : done
                  ? 'text-gray-500 dark:text-gray-400'
                  : 'text-gray-300 dark:text-gray-600'
            }
          >
            {index + 1}. {label}
          </div>
        )
      })}
    </div>
  )
}

/**
 * Order creation wizard: header -> items (with a best-effort client-side
 * stock check) -> optional shipment. The order and its items are created
 * atomically via POST /api/orders/with-items; the shipment (if requested)
 * is a separate call afterwards, since it targets a different resource.
 */
export default function OrderWizardPage() {
  const navigate = useNavigate()
  const [step, setStep] = useState<1 | 2 | 3>(1)
  const [header, setHeader] = useState<WizardHeader>({
    unitId: undefined,
    warehouseId: undefined,
    dateCreated: new Date().toISOString().slice(0, 10),
    status: 'CREATED',
  })
  const [items, setItems] = useState<DraftItem[]>([])
  const [banner, setBanner] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleCreate(shipment: WizardShipment) {
    setBanner(null)
    setSubmitting(true)
    try {
      const orderResponse = await api.post<{ id: number }>('/orders/with-items', {
        header: {
          unitId: header.unitId,
          warehouseId: header.warehouseId,
          dateCreated: header.dateCreated,
          status: header.status,
        },
        items: items.map((item) => ({ resourceId: item.resourceId, quantity: item.quantity })),
      })
      const orderId = orderResponse.data.id

      if (shipment.enabled) {
        try {
          // No warehouseId: the shipment inherits it from the order.
          await api.post('/shipments', {
            orderId,
            vehicleId: shipment.vehicleId,
            status: shipment.status,
          })
        } catch (shipmentError) {
          navigate(`/orders/${orderId}`, {
            state: {
              banner: `Order created, but the shipment could not be created: ${extractApiError(shipmentError).message}`,
            },
          })
          return
        }
      }

      navigate(`/orders/${orderId}`)
    } catch (error) {
      setBanner(extractApiError(error).message)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <FormPage title="New order" backTo="/orders" wide>
      <Stepper current={step} />
      <FormBanner message={banner} />
      <div className={banner ? 'mt-4' : ''}>
        {step === 1 && (
          <HeaderStep
            initial={header}
            onNext={(value) => {
              setHeader(value)
              setStep(2)
            }}
            onCancel={() => navigate('/orders')}
          />
        )}
        {step === 2 && header.warehouseId !== undefined && (
          <ItemsStep
            warehouseId={header.warehouseId}
            items={items}
            onChange={setItems}
            onNext={() => setStep(3)}
            onBack={() => setStep(1)}
          />
        )}
        {step === 3 && header.warehouseId !== undefined && (
          <ShipmentStep
            warehouseId={header.warehouseId}
            initial={{ enabled: false, status: 'PLANNED' }}
            submitting={submitting}
            onSubmit={handleCreate}
            onBack={() => setStep(2)}
          />
        )}
      </div>
    </FormPage>
  )
}
