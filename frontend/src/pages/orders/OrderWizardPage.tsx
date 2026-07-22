import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { api } from '../../api/client'
import type { OrderItemEntity } from '../../api/entities'
import { extractApiError } from '../../api/errors'
import { FormBanner } from '../../components/form/fields'
import { FormPage } from '../../components/form/FormPage'
import type { PageResponse } from '../../components/table/useServerTable'
import HeaderStep from './wizard/HeaderStep'
import ItemsStep from './wizard/ItemsStep'
import ShipmentStep from './wizard/ShipmentStep'
import type { DraftItem, WizardHeader, WizardShipment } from './wizard/shared'

function Stepper({ current }: { current: 1 | 2 | 3 }) {
  const { t } = useTranslation()
  const steps = [t('orders.wizard.stepHeader'), t('orders.wizard.stepItems'), t('orders.wizard.stepShipment')]
  return (
    <div className="mb-4 flex gap-4 text-sm">
      {steps.map((label, index) => {
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
  const { t } = useTranslation()
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
          // The order's real items now exist with real ids; match them back to
          // the draft items by resourceId to build the shipment's item lines.
          const createdItems = await api.get<PageResponse<OrderItemEntity>>('/order-items', {
            params: { orderId, page: 0, size: 100 },
          })
          const itemIdByResourceId = new Map(createdItems.data.content.map((i) => [i.resourceId, i.id]))
          const shipmentItems = shipment.items
            .map((line) => ({ orderItemId: itemIdByResourceId.get(line.resourceId), quantity: line.quantity }))
            .filter((line): line is { orderItemId: number; quantity: number } => line.orderItemId !== undefined)

          // No warehouseId: the shipment inherits it from the order.
          await api.post('/shipments', {
            orderId,
            vehicleId: shipment.vehicleId,
            status: shipment.status,
            items: shipmentItems,
          })
        } catch (shipmentError) {
          navigate(`/orders/${orderId}`, {
            state: {
              banner: t('orders.wizard.shipmentCreateFailed', {
                message: extractApiError(shipmentError).message,
              }),
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
    <FormPage title={t('orders.wizard.title')} backTo="/orders" wide>
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
            draftItems={items}
            initial={{ enabled: false, status: 'PLANNED', items: [] }}
            submitting={submitting}
            onSubmit={handleCreate}
            onBack={() => setStep(2)}
          />
        )}
      </div>
    </FormPage>
  )
}
