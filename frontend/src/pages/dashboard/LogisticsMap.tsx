import { useEffect, useMemo, useRef, useState } from 'react'
import 'leaflet/dist/leaflet.css'
import { MapContainer, Marker, Polyline, TileLayer, useMap } from 'react-leaflet'
import type { Polyline as LeafletPolyline } from 'leaflet'
import { api } from '../../api/client'
import { unitIcon, warehouseIcon } from './mapIcons'
import type { MapData, ShipmentRoute, UnitPin, WarehousePin } from './mapTypes'

const ROUTE_COLOR: Record<ShipmentRoute['status'], string> = {
  PLANNED: '#92a466',
  IN_TRANSIT: '#ca8a04',
  DELIVERED: '#16a34a',
}

type Selected =
  | { kind: 'warehouse'; pin: WarehousePin }
  | { kind: 'unit'; pin: UnitPin }
  | null

/** Animates its dash pattern to suggest movement along the route. */
function AnimatedRoute({ route }: { route: ShipmentRoute }) {
  const ref = useRef<LeafletPolyline | null>(null)

  useEffect(() => {
    if (route.status !== 'IN_TRANSIT') return
    let offset = 0
    const id = setInterval(() => {
      offset = (offset - 1 + 24) % 24
      ref.current?.setStyle({ dashOffset: String(offset) })
    }, 80)
    return () => clearInterval(id)
  }, [route.status])

  const positions: [number, number][] = [
    [route.origin.latitude, route.origin.longitude],
    [route.destination.latitude, route.destination.longitude],
  ]

  return (
    <Polyline
      ref={ref}
      positions={positions}
      pathOptions={{
        color: ROUTE_COLOR[route.status],
        weight: route.status === 'IN_TRANSIT' ? 3 : 2,
        opacity: route.status === 'DELIVERED' ? 0.5 : 0.9,
        dashArray: route.status === 'DELIVERED' ? undefined : '8 8',
      }}
    />
  )
}

/** Recenters the map when the user searches for a pin by name. */
function FlyToOnSearch({ target }: { target: [number, number] | null }) {
  const map = useMap()
  useEffect(() => {
    if (target) map.flyTo(target, Math.max(map.getZoom(), 6))
  }, [target, map])
  return null
}

export default function LogisticsMap() {
  const [data, setData] = useState<MapData | null>(null)
  const [error, setError] = useState(false)
  const [selected, setSelected] = useState<Selected>(null)
  const [search, setSearch] = useState('')
  const [flyTarget, setFlyTarget] = useState<[number, number] | null>(null)
  const [activeShipmentsOnly, setActiveShipmentsOnly] = useState(false)
  const [lowStockOnly, setLowStockOnly] = useState(false)

  useEffect(() => {
    let cancelled = false
    api
      .get<MapData>('/map')
      .then((response) => {
        if (!cancelled) setData(response.data)
      })
      .catch(() => {
        if (!cancelled) setError(true)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const warehouses = useMemo(
    () =>
      (data?.warehouses ?? []).filter(
        (w) => !lowStockOnly || w.stockStatus === 'WARNING' || w.stockStatus === 'CRITICAL',
      ),
    [data, lowStockOnly],
  )
  const units = data?.units ?? []
  const routes = useMemo(
    () =>
      (data?.shipments ?? []).filter((s) => !activeShipmentsOnly || s.status === 'IN_TRANSIT'),
    [data, activeShipmentsOnly],
  )

  function handleSearch(event: React.FormEvent) {
    event.preventDefault()
    const query = search.trim().toLowerCase()
    if (!query) return
    const warehouseMatch = warehouses.find((w) => w.name.toLowerCase().includes(query))
    const unitMatch = units.find((u) => u.name.toLowerCase().includes(query))
    const match = warehouseMatch ?? unitMatch
    if (match) setFlyTarget([match.latitude, match.longitude])
  }

  if (error) {
    return (
      <p className="text-sm text-status-critical">The map could not be loaded. Please try again.</p>
    )
  }

  if (!data) {
    return <p className="text-sm text-gray-500 dark:text-gray-400">Loading map…</p>
  }

  if (warehouses.length === 0 && units.length === 0) {
    return (
      <div className="rounded-xl bg-white p-6 text-center shadow-sm dark:bg-gray-900">
        <h2 className="mb-1 text-sm font-semibold text-gray-700 dark:text-gray-200">
          Logistics map
        </h2>
        <p className="text-sm text-gray-400 dark:text-gray-500">
          No warehouses or units have coordinates yet. Add latitude/longitude from their edit
          forms to see them here.
        </p>
      </div>
    )
  }

  const center: [number, number] = [warehouses[0] ?? units[0]].map((p) => [
    p!.latitude,
    p!.longitude,
  ])[0] as [number, number]

  return (
    <div className="rounded-xl bg-white p-4 shadow-sm dark:bg-gray-900">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-3">
        <h2 className="text-sm font-semibold text-gray-700 dark:text-gray-200">Logistics map</h2>
        <div className="flex flex-wrap items-center gap-3 text-sm">
          <form onSubmit={handleSearch} className="flex gap-1">
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search warehouse or unit…"
              className="rounded border border-gray-300 bg-white px-2 py-1 text-xs dark:border-gray-700 dark:bg-gray-800"
            />
          </form>
          <label className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
            <input
              type="checkbox"
              checked={activeShipmentsOnly}
              onChange={(e) => setActiveShipmentsOnly(e.target.checked)}
            />
            Active shipments only
          </label>
          <label className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
            <input
              type="checkbox"
              checked={lowStockOnly}
              onChange={(e) => setLowStockOnly(e.target.checked)}
            />
            Low stock only
          </label>
        </div>
      </div>

      <div className="flex flex-col gap-3 lg:flex-row">
        <div className="h-[420px] w-full overflow-hidden rounded-lg lg:flex-1">
          <MapContainer center={center} zoom={6} scrollWheelZoom style={{ height: '100%', width: '100%' }}>
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <FlyToOnSearch target={flyTarget} />
            {routes.map((route) => (
              <AnimatedRoute key={route.id} route={route} />
            ))}
            {warehouses.map((warehouse) => (
              <Marker
                key={`warehouse-${warehouse.id}`}
                position={[warehouse.latitude, warehouse.longitude]}
                icon={warehouseIcon(
                  warehouse.stockStatus,
                  selected?.kind === 'warehouse' && selected.pin.id === warehouse.id,
                )}
                eventHandlers={{ click: () => setSelected({ kind: 'warehouse', pin: warehouse }) }}
              />
            ))}
            {units.map((unit) => (
              <Marker
                key={`unit-${unit.id}`}
                position={[unit.latitude, unit.longitude]}
                icon={unitIcon(selected?.kind === 'unit' && selected.pin.id === unit.id)}
                eventHandlers={{ click: () => setSelected({ kind: 'unit', pin: unit }) }}
              />
            ))}
          </MapContainer>
        </div>

        <div className="w-full shrink-0 rounded-lg border border-gray-100 p-3 text-sm dark:border-gray-800 lg:w-56">
          {selected === null ? (
            <p className="text-gray-400 dark:text-gray-500">Click a pin for details.</p>
          ) : selected.kind === 'warehouse' ? (
            <div>
              <div className="font-semibold">{selected.pin.name}</div>
              <div className="text-xs text-gray-500 dark:text-gray-400">Warehouse</div>
              <div
                className={`mt-2 text-xs font-medium ${
                  selected.pin.stockStatus === 'CRITICAL'
                    ? 'text-status-critical'
                    : selected.pin.stockStatus === 'WARNING'
                      ? 'text-status-warn'
                      : 'text-status-ok'
                }`}
              >
                Stock: {selected.pin.stockStatus}
              </div>
            </div>
          ) : (
            <div>
              <div className="font-semibold">{selected.pin.name}</div>
              <div className="text-xs text-gray-500 dark:text-gray-400">Unit</div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
