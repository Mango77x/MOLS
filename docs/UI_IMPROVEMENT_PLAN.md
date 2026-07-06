# MOLS UI Improvement Plan

> ⚠️ **Note**: Once this plan is fully implemented and the React migration is complete, this document should be deleted. It's a living blueprint for the migration process only.

## Current State
- **Framework**: Thymeleaf (server-rendered)
- **UI Kit**: Bootstrap 5
- **Theme**: Dark/Light mode
- **Problem**: Generic admin panel, weak UX, no visualizations

## Vision
Transform MOLS from "functional tool" to "professional product" users actually enjoy using.

---

## Phase 1: React Migration (Foundation)

### Setup
```bash
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install -D tailwindcss postcss autoprefixer
npm install react-router-dom axios zustand
```

### Key Decision: Modern Stack
| Layer | Current | New | Why |
|-------|---------|-----|-----|
| Rendering | Thymeleaf | React | Interactive, reusable components |
| Styling | Bootstrap | Tailwind | Utility-first, modern design |
| Charts | None | Recharts | Beautiful visualizations |
| Maps | None | Leaflet | Free, no vendor lock-in |
| Forms | HTML | React Hook Form | Validation, UX |

---

## Phase 2: Dashboard Redesign

### Current: 6 KPI Cards (boring)
### New: Interactive Dashboard

**Components**:
1. **KPI Cards with State Indicators**
   - Color: Green (good) → Yellow (warning) → Red (critical)
   - Icon: Clear visual meaning
   - Trend: Up/down indicators

2. **Map View** ⭐ (Core Feature)
   - Show warehouses, units, shipments in real-time
   - Color-coded pins (stock status)
   - Animated shipment routes
   - Click → Details modal

3. **Charts Section**
   - Stock trends (area chart)
   - Orders over time (bar chart)
   - Shipment status breakdown (pie chart)

4. **Alerts Panel**
   - Not generic badges
   - Contextual cards with action buttons
   - Dismiss/resolve actions

---

## Phase 3: Logistics Map 🗺️ (Differentiator)

### Why Maps Matter for Military Logistics
- Spatial awareness (where are assets?)
- Visual understanding of network
- Identify bottlenecks, optimization opportunities

### Map MVP Spec
```
✅ Warehouses as pins (color = stock status)
✅ Units as pins (different icon)
✅ Shipments as animated lines (source → destination)
✅ Click pin → details sidebar
✅ Filters (active shipments, low stock, etc.)
✅ Zoom/Pan/Search by location
❌ Routing (Phase 2 maybe)
❌ Traffic/weather overlay (Phase 3)
```

### Tech
```
npm install react-leaflet leaflet
```

### Why OpenStreetMap (not Google Maps)?
- **Cost**: Free vs $7/1000 requests
- **Control**: Yours forever vs Google's terms
- **Simplicity**: 40KB vs bloated SDK
- **Sufficient**: Perfect for internal tool

---

## Phase 4: Smart Data Tables

### Current: Plain HTML tables
### New: Professional tables

**Features**:
- **Inline search + filters** (visible, not URL params)
- **Column sorting** with visual indicator
- **Inline actions** (edit/delete without navigation)
- **Pagination** that doesn't suck
- **Responsive** (horizontal scroll on mobile)

### Tech
```
npm install @tanstack/react-table
```

---

## Phase 5: Enhanced Forms

### Current: Bootstrap form-controls
### New: Smart, guided forms

**UX Improvements**:
1. **Real-time validation** (green check = good, red X = fix)
2. **Visual previews** (select warehouse → see on map)
3. **Wizard flows** (order → items → shipment in steps)
4. **Autocomplete** (type resource name → suggestions)
5. **Field guidance** (helper text, examples)

### Tech
```
npm install react-hook-form @hookform/resolvers
```

---

## Phase 6: Kanban Board (optional)

**Shipment Workflow Visualization**:
```
PLANNED | IN_TRANSIT | DELIVERED
```
- Drag-drop to transition
- Real-time updates via WebSocket
- Quick actions per card

---

## Design System

### Colors
- **Primary**: Military green (current palette OK)
- **Status**: Green (OK) → Yellow (Warning) → Red (Critical)
- **Neutral**: Gray for secondary info
- **Surface**: Light/dark based on theme

### Typography
- Headings: Bold, clear hierarchy
- Body: 14-16px for readability
- Mono: Code/technical data

### Spacing
- Generous whitespace
- Consistent 8px grid
- Breathing room between sections

---

## Development Flow

```bash
# Backend (Spring Boot)
cd /path/to/MOLS
./mvnw spring-boot:run

# Frontend (React)
cd /path/to/MOLS/frontend
npm run dev

# Both: Docker
docker-compose up
```

---

## Rollout Strategy

### Option A: Incremental
1. New React app runs alongside Thymeleaf
2. Migrate one page at a time
3. Users choose old vs new
4. Deprecate Thymeleaf once React is feature-complete

### Option B: Big Bang
1. Rewrite entire frontend in React
2. Deploy all at once
3. Faster, but riskier

**Recommendation**: Option A (safer, smaller commits)

---

## Effort Estimate

| Phase | Days | Impact |
|-------|------|--------|
| Setup & Tooling | 2 | Foundation |
| Dashboard + KPIs | 3 | 30% wow factor |
| **Map Component** | **3** | **70% wow factor** |
| Tables | 5 | Functionality |
| Forms | 5 | Usability |
| Polish | 3 | Professionalism |
| **Total** | **~21 days** | **Production-ready** |

Can be split into smaller sprints. **Map should be #1 priority** (biggest visual impact).

---

## Backend Prep

### Must-Have Updates
```java
// Warehouse.java
private Double latitude;
private Double longitude;

// Unit.java  
private Double latitude;
private Double longitude;
```

### Nice-to-Have
- WebSocket for real-time shipment tracking
- Geospatial queries (warehouses in radius)
- Batch endpoints to reduce API calls

---

## Success Metrics

✅ Users say "This looks professional"  
✅ Map is the first thing they see (they love it)  
✅ Data tables feel responsive and smooth  
✅ Forms guide them without frustration  
✅ Zero performance issues  

---

## Next Steps

1. [ ] Approve this plan
2. [ ] Set up React project scaffold
3. [ ] Design component structure
4. [ ] Start with Dashboard (KPIs)
5. [ ] Build Map component (core feature)
6. [ ] Migrate tables
7. [ ] Polish forms
8. [ ] Deploy & gather feedback

---

**Decision Point**: Start building? Or iterate on design first?
