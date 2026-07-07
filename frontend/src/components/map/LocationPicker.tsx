import 'leaflet/dist/leaflet.css'
import { MapContainer, Marker, TileLayer, useMapEvents } from 'react-leaflet'
import { warehouseIcon } from '../../pages/dashboard/mapIcons'

const DEFAULT_CENTER: [number, number] = [40.4168, -3.7038] // Madrid — a neutral default when no coordinates are set yet

function ClickToPlace({ onPick }: { onPick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(event) {
      onPick(event.latlng.lat, event.latlng.lng)
    },
  })
  return null
}

/**
 * Click-to-place map picker for latitude/longitude fields (Warehouse/Unit
 * forms). Reuses the same tile layer as the dashboard's logistics map
 * (`pages/dashboard/LogisticsMap.tsx`) for visual consistency.
 */
export default function LocationPicker({
  latitude,
  longitude,
  onChange,
}: {
  latitude: number | null
  longitude: number | null
  onChange: (lat: number, lng: number) => void
}) {
  const hasPosition =
    typeof latitude === 'number' &&
    typeof longitude === 'number' &&
    !Number.isNaN(latitude) &&
    !Number.isNaN(longitude)
  const center: [number, number] = hasPosition ? [latitude, longitude] : DEFAULT_CENTER

  return (
    <div className="h-64 w-full overflow-hidden rounded-lg border border-gray-300 dark:border-gray-700">
      <MapContainer
        center={center}
        zoom={hasPosition ? 8 : 4}
        scrollWheelZoom
        style={{ height: '100%', width: '100%' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
        <ClickToPlace onPick={onChange} />
        {hasPosition && <Marker position={[latitude, longitude]} icon={warehouseIcon('OK', false)} />}
      </MapContainer>
    </div>
  )
}
