package com.mls.logistics.web;

import com.mls.logistics.config.DashboardProperties;
import com.mls.logistics.domain.Order;
import com.mls.logistics.domain.OrderStatus;
import com.mls.logistics.domain.ShipmentStatus;
import com.mls.logistics.domain.Stock;
import com.mls.logistics.service.OrderService;
import com.mls.logistics.service.OrderItemService;
import com.mls.logistics.service.MovementService;
import com.mls.logistics.service.ResourceService;
import com.mls.logistics.service.ShipmentService;
import com.mls.logistics.service.StockService;
import com.mls.logistics.service.UnitService;
import com.mls.logistics.service.VehicleService;
import com.mls.logistics.service.WarehouseService;
import com.mls.logistics.dto.request.AdjustStockRequest;
import com.mls.logistics.dto.request.CreateOrderItemRequest;
import com.mls.logistics.dto.request.CreateOrderRequest;
import com.mls.logistics.dto.request.CreateResourceRequest;
import com.mls.logistics.dto.request.CreateShipmentRequest;
import com.mls.logistics.dto.request.CreateUnitRequest;
import com.mls.logistics.dto.request.CreateVehicleRequest;
import com.mls.logistics.dto.request.CreateWarehouseRequest;
import com.mls.logistics.dto.request.CreateStockRequest;
import com.mls.logistics.dto.request.UpdateOrderItemRequest;
import com.mls.logistics.dto.request.UpdateOrderRequest;
import com.mls.logistics.dto.request.UpdateResourceRequest;
import com.mls.logistics.dto.request.UpdateShipmentRequest;
import com.mls.logistics.dto.request.UpdateUnitRequest;
import com.mls.logistics.dto.request.UpdateVehicleRequest;
import com.mls.logistics.dto.request.UpdateWarehouseRequest;
import com.mls.logistics.exception.InsufficientStockException;
import com.mls.logistics.exception.InvalidRequestException;
import com.mls.logistics.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Comparator;

/**
 * Thymeleaf UI controller for the admin web interface.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *     <li>Serve server-rendered pages under {@code /ui/**}</li>
 *     <li>Provide admin-friendly validation and flash messages</li>
 *     <li>Delegate all business logic to services (no direct repository access)</li>
 *     <li>Support server-side sorting for list tables</li>
 * </ul>
 */
@Controller
@RequestMapping
public class UiController {

    private static final String SESSION_ORDER_DRAFT_ITEMS = "orderDraftItems";
    private static final String SESSION_ORDER_DRAFT_HEADER = "orderDraftHeader";

    private final WarehouseService warehouseService;
    private final VehicleService vehicleService;
    private final OrderService orderService;
    private final OrderItemService orderItemService;
    private final UnitService unitService;
    private final ResourceService resourceService;
    private final ShipmentService shipmentService;
    private final StockService stockService;
    private final MovementService movementService;
    private final DashboardProperties dashboardProperties;

    public UiController(WarehouseService warehouseService,
                        VehicleService vehicleService,
                        OrderService orderService,
                        OrderItemService orderItemService,
                        UnitService unitService,
                        ResourceService resourceService,
                        ShipmentService shipmentService,
                        StockService stockService,
                        MovementService movementService,
                        DashboardProperties dashboardProperties) {
        this.warehouseService = warehouseService;
        this.vehicleService = vehicleService;
        this.orderService = orderService;
        this.orderItemService = orderItemService;
        this.unitService = unitService;
        this.resourceService = resourceService;
        this.shipmentService = shipmentService;
        this.stockService = stockService;
        this.movementService = movementService;
        this.dashboardProperties = dashboardProperties;
    }

    /**
     * Root route.
     *
     * <p>Redirects to the dashboard under {@code /ui}.</p>
     */
    @GetMapping("/")
    public String root() {
        return "redirect:/ui";
    }

    /**
     * Dashboard landing page.
     */
    @GetMapping("/ui")
    public String dashboard(Model model) {
        int lowStockThreshold = dashboardProperties.getLowStockThreshold();
        int criticalStockThreshold = dashboardProperties.getCriticalStockThreshold();

        long totalOrders = safeLong(orderService::getTotalOrdersCount, model);
        long completedOrders = safeLong(() -> orderService.countByStatus(OrderStatus.COMPLETED), model);
        long pendingOrders = safeLong(() -> orderService.countByStatus(OrderStatus.CREATED)
                + orderService.countByStatus(OrderStatus.VALIDATED), model);

        long activeShipments = safeLong(() -> shipmentService.countByStatus(ShipmentStatus.IN_TRANSIT), model);

        Map<String, Long> stockByWarehouse = safeMap(stockService::getStockQuantityByWarehouse, model);
        long totalStockQuantity = stockByWarehouse.values().stream().mapToLong(Long::longValue).sum();
        int stockWarehouseCount = stockByWarehouse.size();

        long lowStockCount = safeLong(() -> stockService.countByQuantityLessThan(lowStockThreshold), model);

        LocalDateTime now = LocalDateTime.now();
        long recentMovementsCount = safeLong(
            () -> movementService.countByDateTimeAfter(now.minusHours(dashboardProperties.getRecentActivityHours())),
            model
        );

        double fulfillmentRatePercent = safeDouble(orderService::getFulfillmentRate, model);
        boolean fulfillmentTargetMet = fulfillmentRatePercent >= dashboardProperties.getFulfillmentTargetPercent();

        // Recent movements (table)
        List<com.mls.logistics.domain.Movement> recentMovements = safeList(movementService::getRecentMovements, model);

        // Alerts: low stock
        List<Stock> lowStockItemsRaw = safeList(() -> stockService.getLowStockItems(lowStockThreshold), model);
        lowStockItemsRaw.sort(Comparator
            .comparingInt(Stock::getQuantity)
            .thenComparing(Stock::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        int lowStockLimit = dashboardProperties.getLowStockListLimit();
        List<Stock> lowStockItems = lowStockItemsRaw.stream().limit(lowStockLimit).toList();

        // Alerts: stale orders
        List<Order> staleOrdersRaw = safeList(() -> orderService.getStaleOrders(dashboardProperties.getStaleOrderDays()), model);
        LocalDate today = LocalDate.now();
        List<StaleOrderView> staleOrdersView = new ArrayList<>();
        for (Order order : staleOrdersRaw) {
            long daysPending = 0;
            if (order.getDateCreated() != null) {
            daysPending = ChronoUnit.DAYS.between(order.getDateCreated(), today);
            }
            String unitName = (order.getUnit() != null && order.getUnit().getName() != null)
                ? order.getUnit().getName()
                : "—";
            staleOrdersView.add(new StaleOrderView(order.getId(), unitName, daysPending));
        }
        staleOrdersView.sort(Comparator
            .comparingLong(StaleOrderView::daysPending).reversed()
            .thenComparing(StaleOrderView::orderId, Comparator.nullsLast(Comparator.naturalOrder())));
        int staleLimit = dashboardProperties.getStaleOrdersListLimit();
        List<StaleOrderView> staleOrders = staleOrdersView.stream().limit(staleLimit).toList();

        // Charts
        List<String> stockWarehouseLabels = new ArrayList<>(stockByWarehouse.keySet());
        List<Long> stockWarehouseValues = stockWarehouseLabels.stream().map(stockByWarehouse::get).toList();

        Map<String, Long> movementsByType = safeMap(
            () -> movementService.getMovementCountByType(now.minusDays(dashboardProperties.getMovementChartDays())),
            model
        );
        long entryCount = movementsByType.getOrDefault("ENTRY", 0L);
        long exitCount = movementsByType.getOrDefault("EXIT", 0L);
        long movementsTotal = entryCount + exitCount;

        long cancelledOrders = safeLong(() -> orderService.countByStatus(OrderStatus.CANCELLED), model);

        Map<String, Object> chartData = new HashMap<>();
        chartData.put("stockByWarehouse", Map.of(
            "labels", stockWarehouseLabels,
            "values", stockWarehouseValues
        ));
        chartData.put("movementsByType", Map.of(
            "labels", List.of("ENTRY", "EXIT"),
            "values", List.of(entryCount, exitCount),
            "total", movementsTotal
        ));
        chartData.put("ordersByStatus", Map.of(
            "labels", List.of("PENDING", "COMPLETED", "CANCELLED"),
            "values", List.of(pendingOrders, completedOrders, cancelledOrders)
        ));

        model.addAttribute("totalOrders", totalOrders);
        model.addAttribute("pendingOrders", pendingOrders);

        model.addAttribute("totalStockQuantity", totalStockQuantity);
        model.addAttribute("stockWarehouseCount", stockWarehouseCount);

        model.addAttribute("activeShipments", activeShipments);
        model.addAttribute("lowStockCount", lowStockCount);
        model.addAttribute("recentMovementsCount", recentMovementsCount);

        model.addAttribute("fulfillmentRatePercent", fulfillmentRatePercent);
        model.addAttribute("fulfillmentTargetMet", fulfillmentTargetMet);
        model.addAttribute("fulfillmentTargetPercent", dashboardProperties.getFulfillmentTargetPercent());

        model.addAttribute("recentMovements", recentMovements);

        model.addAttribute("lowStockItems", lowStockItems);
        model.addAttribute("lowStockTotalCount", lowStockCount);
        model.addAttribute("lowStockThreshold", lowStockThreshold);
        model.addAttribute("criticalStockThreshold", criticalStockThreshold);
        model.addAttribute("lowStockLimit", lowStockLimit);

        model.addAttribute("staleOrders", staleOrders);
        model.addAttribute("staleOrdersDays", dashboardProperties.getStaleOrderDays());

        model.addAttribute("chartData", chartData);

        model.addAttribute("hasOrders", totalOrders > 0);
        model.addAttribute("hasStock", !stockWarehouseLabels.isEmpty());
        model.addAttribute("hasMovements", !recentMovements.isEmpty());
        model.addAttribute("hasMovementChartData", movementsTotal > 0);
        model.addAttribute("hasOrdersChartData", totalOrders > 0);
        model.addAttribute("hasLowStock", lowStockCount > 0);
        model.addAttribute("hasStaleOrders", !staleOrdersRaw.isEmpty());
        return "ui/dashboard";
    }

    /**
     * Warehouses list page.
     */
    @GetMapping("/ui/warehouses")
    public String warehouses(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "id" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "asc" : dir.trim().toLowerCase();
        Sort.Direction direction = "desc".equals(dirValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "location" -> "location";
            case "name" -> "name";
            default -> "name";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("warehouses", safeList(() -> warehouseService.getAllWarehouses(sorting), model));
        return "ui/warehouses";
    }

    /**
     * Resources list page.
     */
    @GetMapping("/ui/resources")
    public String resources(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "id" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "asc" : dir.trim().toLowerCase();
        Sort.Direction direction = "desc".equals(dirValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "name" -> "name";
            case "type" -> "type";
            case "criticality" -> "criticality";
            default -> "id";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("resources", safeList(() -> resourceService.getAllResources(sorting), model));
        return "ui/resources";
    }

    /**
     * Resource create form.
     */
    @GetMapping("/ui/resources/new")
    public String newResource(Model model) {
        if (!model.containsAttribute("resourceForm")) {
            model.addAttribute("resourceForm", new CreateResourceRequest());
        }
        model.addAttribute("formMode", "create");
        return "ui/resource-form";
    }

    /**
     * Create resource action.
     */
    @PostMapping("/ui/resources")
    public String createResource(
            @Valid @ModelAttribute("resourceForm") CreateResourceRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "create");
            return "ui/resource-form";
        }

        try {
            resourceService.createResource(form);
            redirectAttributes.addFlashAttribute("successMessage", "Resource created successfully.");
            return "redirect:/ui/resources";
        } catch (DataAccessException ex) {
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/resource-form";
        }
    }

    /**
     * Shipments list page.
     */
    @GetMapping("/ui/shipments")
    public String shipments(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "id" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "asc" : dir.trim().toLowerCase();
        Sort.Direction direction = "desc".equals(dirValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "order" -> "order.id";
            case "vehicle" -> "vehicle.id";
            case "warehouse" -> "warehouse.id";
            case "status" -> "status";
            default -> "id";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("shipments", safeList(() -> shipmentService.getAllShipments(sorting), model));
        return "ui/shipments";
    }

    /**
     * Shipment detail page (traceability view).
     */
    @GetMapping("/ui/shipments/{id}")
    public String shipmentDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var shipment = shipmentService.getShipmentById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));

            Sort itemSort = Sort.by(Sort.Direction.ASC, "id");
            Sort movementSort = Sort.by(Sort.Direction.DESC, "dateTime").and(Sort.by(Sort.Direction.DESC, "id"));

            model.addAttribute("shipment", shipment);

            Long orderId = shipment.getOrder() != null ? shipment.getOrder().getId() : null;
            if (orderId != null) {
                model.addAttribute("items", safeList(() -> orderItemService.getOrderItemsByOrderId(orderId, itemSort), model));
            } else {
                model.addAttribute("items", List.of());
            }

            model.addAttribute("movements", safeList(() -> movementService.getMovementsByShipmentId(id, movementSort), model));
            return "ui/shipment-detail";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Shipment not found.");
            return "redirect:/ui/shipments";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/shipments";
        }
    }

    /**
     * Shipment create form.
     */
    @GetMapping("/ui/shipments/new")
    public String newShipment(Model model) {
        if (!model.containsAttribute("shipmentForm")) {
            model.addAttribute("shipmentForm", new CreateShipmentRequest());
        }
        populateShipmentReferenceData(model);
        model.addAttribute("formMode", "create");
        return "ui/shipment-form";
    }

    /**
     * Create shipment action.
     */
    @PostMapping("/ui/shipments")
    public String createShipment(
            @Valid @ModelAttribute("shipmentForm") CreateShipmentRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            populateShipmentReferenceData(model);
            model.addAttribute("formMode", "create");
            return "ui/shipment-form";
        }

        try {
            shipmentService.createShipment(form);
            redirectAttributes.addFlashAttribute("successMessage", "Shipment created successfully.");
            return "redirect:/ui/shipments";
        } catch (InsufficientStockException | InvalidRequestException ex) {
            populateShipmentReferenceData(model);
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/shipment-form";
        } catch (DataIntegrityViolationException ex) {
            populateShipmentReferenceData(model);
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "Please verify the selected order, vehicle, and warehouse.");
            return "ui/shipment-form";
        } catch (DataAccessException ex) {
            populateShipmentReferenceData(model);
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/shipment-form";
        }
    }

    /**
     * Shipment edit form.
     */
    @GetMapping("/ui/shipments/{id}/edit")
    public String editShipment(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var shipment = shipmentService.getShipmentById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Shipment", "id", id));

            if (!model.containsAttribute("shipmentForm")) {
                CreateShipmentRequest form = new CreateShipmentRequest();
                if (shipment.getOrder() != null) {
                    form.setOrderId(shipment.getOrder().getId());
                }
                if (shipment.getVehicle() != null) {
                    form.setVehicleId(shipment.getVehicle().getId());
                }
                if (shipment.getWarehouse() != null) {
                    form.setWarehouseId(shipment.getWarehouse().getId());
                }
                form.setStatus(shipment.getStatus() != null ? shipment.getStatus().name() : null);
                model.addAttribute("shipmentForm", form);
            }

            populateShipmentReferenceData(model);
            model.addAttribute("shipmentId", id);
            model.addAttribute("formMode", "edit");
            return "ui/shipment-form";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Shipment not found.");
            return "redirect:/ui/shipments";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/shipments";
        }
    }

    /**
     * Update shipment action.
     *
     * <p>If the status transitions to {@code DELIVERED}, fulfillment may occur and can fail with
     * {@link InsufficientStockException}.</p>
     */
    @PostMapping("/ui/shipments/{id}")
    public String updateShipment(
            @PathVariable Long id,
            @Valid @ModelAttribute("shipmentForm") CreateShipmentRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            populateShipmentReferenceData(model);
            model.addAttribute("shipmentId", id);
            model.addAttribute("formMode", "edit");
            return "ui/shipment-form";
        }

        try {
            UpdateShipmentRequest request = new UpdateShipmentRequest();
            request.setOrderId(form.getOrderId());
            request.setVehicleId(form.getVehicleId());
            request.setWarehouseId(form.getWarehouseId());
            request.setStatus(form.getStatus());

            shipmentService.updateShipment(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Shipment updated successfully.");
            return "redirect:/ui/shipments";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Shipment not found.");
            return "redirect:/ui/shipments";
        } catch (InsufficientStockException | InvalidRequestException ex) {
            populateShipmentReferenceData(model);
            model.addAttribute("shipmentId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/shipment-form";
        } catch (DataIntegrityViolationException ex) {
            populateShipmentReferenceData(model);
            model.addAttribute("shipmentId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "Please verify the selected order, vehicle, and warehouse.");
            return "ui/shipment-form";
        } catch (DataAccessException ex) {
            populateShipmentReferenceData(model);
            model.addAttribute("shipmentId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/shipment-form";
        }
    }

    /**
     * Delete shipment action.
     */
    @PostMapping("/ui/shipments/{id}/delete")
    public String deleteShipment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            shipmentService.deleteShipment(id);
            redirectAttributes.addFlashAttribute("successMessage", "Shipment deleted successfully.");
        } catch (InvalidRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Shipment not found.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "This shipment is in use and cannot be deleted.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/shipments";
    }

    /**
     * Resource edit form.
     *
     * @param id resource identifier
     */
    @GetMapping("/ui/resources/{id}/edit")
    public String editResource(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var resource = resourceService.getResourceById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Resource", "id", id));

            if (!model.containsAttribute("resourceForm")) {
                CreateResourceRequest form = new CreateResourceRequest();
                form.setName(resource.getName());
                form.setType(resource.getType());
                form.setCriticality(resource.getCriticality());
                model.addAttribute("resourceForm", form);
            }

            model.addAttribute("resourceId", id);
            model.addAttribute("formMode", "edit");
            return "ui/resource-form";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resource not found.");
            return "redirect:/ui/resources";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/resources";
        }
    }

    /**
     * Update resource action.
     *
     * @param id resource identifier
     */
    @PostMapping("/ui/resources/{id}")
    public String updateResource(
            @PathVariable Long id,
            @Valid @ModelAttribute("resourceForm") CreateResourceRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("resourceId", id);
            model.addAttribute("formMode", "edit");
            return "ui/resource-form";
        }

        try {
            UpdateResourceRequest request = new UpdateResourceRequest();
            request.setName(form.getName());
            request.setType(form.getType());
            request.setCriticality(form.getCriticality());

            resourceService.updateResource(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Resource updated successfully.");
            return "redirect:/ui/resources";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Resource not found.");
            return "redirect:/ui/resources";
        } catch (DataAccessException ex) {
            model.addAttribute("resourceId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/resource-form";
        }
    }

    /**
     * Delete resource action.
     *
     * @param id resource identifier
     */
    @PostMapping("/ui/resources/{id}/delete")
    public String deleteResource(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            resourceService.deleteResource(id);
            redirectAttributes.addFlashAttribute("successMessage", "Resource deleted successfully.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "This resource is in use and cannot be deleted.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/resources";
    }

    /**
     * Warehouse create form.
     */
    @GetMapping("/ui/warehouses/new")
    public String newWarehouse(Model model) {
        if (!model.containsAttribute("warehouseForm")) {
            model.addAttribute("warehouseForm", new CreateWarehouseRequest());
        }
        model.addAttribute("formMode", "create");
        return "ui/warehouse-form";
    }

    /**
     * Create warehouse action.
     */
    @PostMapping("/ui/warehouses")
    public String createWarehouse(
            @Valid @ModelAttribute("warehouseForm") CreateWarehouseRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "create");
            return "ui/warehouse-form";
        }

        try {
            warehouseService.createWarehouse(form);
            redirectAttributes.addFlashAttribute("successMessage", "Warehouse created successfully.");
            return "redirect:/ui/warehouses";
        } catch (DataAccessException ex) {
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/warehouse-form";
        }
    }

    /**
     * Warehouse edit form.
     *
     * @param id warehouse identifier
     */
    @GetMapping("/ui/warehouses/{id}/edit")
    public String editWarehouse(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var warehouse = warehouseService.getWarehouseById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse", "id", id));

            if (!model.containsAttribute("warehouseForm")) {
                CreateWarehouseRequest form = new CreateWarehouseRequest();
                form.setName(warehouse.getName());
                form.setLocation(warehouse.getLocation());
                form.setLatitude(warehouse.getLatitude());
                form.setLongitude(warehouse.getLongitude());
                model.addAttribute("warehouseForm", form);
            }
            model.addAttribute("warehouseId", id);
            model.addAttribute("formMode", "edit");
            return "ui/warehouse-form";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Warehouse not found.");
            return "redirect:/ui/warehouses";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/warehouses";
        }
    }

    /**
     * Update warehouse action.
     *
     * @param id warehouse identifier
     */
    @PostMapping("/ui/warehouses/{id}")
    public String updateWarehouse(
            @PathVariable Long id,
            @Valid @ModelAttribute("warehouseForm") CreateWarehouseRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("warehouseId", id);
            model.addAttribute("formMode", "edit");
            return "ui/warehouse-form";
        }

        try {
            UpdateWarehouseRequest request = new UpdateWarehouseRequest();
            request.setName(form.getName());
            request.setLocation(form.getLocation());
            request.setLatitude(form.getLatitude());
            request.setLongitude(form.getLongitude());

            warehouseService.updateWarehouse(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Warehouse updated successfully.");
            return "redirect:/ui/warehouses";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Warehouse not found.");
            return "redirect:/ui/warehouses";
        } catch (DataAccessException ex) {
            model.addAttribute("warehouseId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/warehouse-form";
        }
    }

    /**
     * Delete warehouse action.
     *
     * @param id warehouse identifier
     */
    @PostMapping("/ui/warehouses/{id}/delete")
    public String deleteWarehouse(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            warehouseService.deleteWarehouse(id);
            redirectAttributes.addFlashAttribute("successMessage", "Warehouse deleted successfully.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete warehouse because it is referenced by other records.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/warehouses";
    }

    /**
     * Vehicles list page.
     */
    @GetMapping("/ui/vehicles")
    public String vehicles(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "id" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "asc" : dir.trim().toLowerCase();
        Sort.Direction direction = "desc".equals(dirValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "capacity" -> "capacity";
            case "status" -> "status";
            case "type" -> "type";
            default -> "type";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("vehicles", safeList(() -> vehicleService.getAllVehicles(sorting), model));
        return "ui/vehicles";
    }

    /**
     * Vehicle create form.
     */
    @GetMapping("/ui/vehicles/new")
    public String newVehicle(Model model) {
        if (!model.containsAttribute("vehicleForm")) {
            model.addAttribute("vehicleForm", new CreateVehicleRequest());
        }
        model.addAttribute("formMode", "create");
        return "ui/vehicle-form";
    }

    /**
     * Create vehicle action.
     */
    @PostMapping("/ui/vehicles")
    public String createVehicle(
            @Valid @ModelAttribute("vehicleForm") CreateVehicleRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "create");
            return "ui/vehicle-form";
        }

        try {
            vehicleService.createVehicle(form);
            redirectAttributes.addFlashAttribute("successMessage", "Vehicle created successfully.");
            return "redirect:/ui/vehicles";
        } catch (InvalidRequestException ex) {
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/vehicle-form";
        } catch (DataAccessException ex) {
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/vehicle-form";
        }
    }

    /**
     * Vehicle edit form.
     *
     * @param id vehicle id
     */
    @GetMapping("/ui/vehicles/{id}/edit")
    public String editVehicle(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var vehicle = vehicleService.getVehicleById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Vehicle", "id", id));

            if (!model.containsAttribute("vehicleForm")) {
                CreateVehicleRequest form = new CreateVehicleRequest();
                form.setType(normalizeVehicleType(vehicle.getType()));
                form.setCapacity(vehicle.getCapacity());
                form.setStatus(vehicle.getStatus() != null ? vehicle.getStatus().name() : null);
                model.addAttribute("vehicleForm", form);
            }

            model.addAttribute("vehicleId", id);
            model.addAttribute("formMode", "edit");
            return "ui/vehicle-form";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vehicle not found.");
            return "redirect:/ui/vehicles";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/vehicles";
        }
    }

    /**
     * Update an existing vehicle.
     *
     * @param id vehicle id
     */
    @PostMapping("/ui/vehicles/{id}")
    public String updateVehicle(
            @PathVariable Long id,
            @Valid @ModelAttribute("vehicleForm") CreateVehicleRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("vehicleId", id);
            model.addAttribute("formMode", "edit");
            return "ui/vehicle-form";
        }

        try {
            UpdateVehicleRequest request = new UpdateVehicleRequest();
            request.setType(form.getType());
            request.setCapacity(form.getCapacity());
            request.setStatus(form.getStatus());

            vehicleService.updateVehicle(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Vehicle updated successfully.");
            return "redirect:/ui/vehicles";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Vehicle not found.");
            return "redirect:/ui/vehicles";
        } catch (InvalidRequestException ex) {
            model.addAttribute("vehicleId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/vehicle-form";
        } catch (DataAccessException ex) {
            model.addAttribute("vehicleId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/vehicle-form";
        }
    }

    /**
     * Delete an existing vehicle.
     *
     * @param id vehicle id
     */
    @PostMapping("/ui/vehicles/{id}/delete")
    public String deleteVehicle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            vehicleService.deleteVehicle(id);
            redirectAttributes.addFlashAttribute("successMessage", "Vehicle deleted successfully.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete vehicle because it is referenced by other records.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/vehicles";
    }

    /**
     * Orders list page.
     *
     * <p>Supports simple sorting via query parameters and also computes an in-memory map of
     * items by order for the list view.</p>
     */
    @GetMapping("/ui/orders")
    public String orders(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "date" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "desc" : dir.trim().toLowerCase();
        Sort.Direction direction = "asc".equals(dirValue) ? Sort.Direction.ASC : Sort.Direction.DESC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "unit" -> "unit.id";
            case "status" -> "status";
            case "date" -> "dateCreated";
            default -> "dateCreated";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        List<com.mls.logistics.domain.Order> orders = safeList(() -> orderService.getAllOrders(sorting), model);
        model.addAttribute("orders", orders);

        List<Long> orderIds = orders.stream()
                .map(com.mls.logistics.domain.Order::getId)
                .filter(id -> id != null)
                .toList();

        Sort itemSort = Sort.by(Sort.Direction.ASC, "order.id")
                .and(Sort.by(Sort.Direction.ASC, "resource.name"))
                .and(Sort.by(Sort.Direction.ASC, "id"));

        List<com.mls.logistics.domain.OrderItem> allItems = safeList(() -> orderItemService.getOrderItemsByOrderIds(orderIds, itemSort), model);
        Map<Long, List<com.mls.logistics.domain.OrderItem>> orderItemsByOrderId = new HashMap<>();
        for (var item : allItems) {
            if (item.getOrder() == null || item.getOrder().getId() == null) {
                continue;
            }
            orderItemsByOrderId.computeIfAbsent(item.getOrder().getId(), k -> new ArrayList<>()).add(item);
        }
        model.addAttribute("orderItemsByOrderId", orderItemsByOrderId);

        return "ui/orders";
    }

    /**
     * Order detail page with traceability.
     *
     * <p>Displays the order header, its items, related shipments, and stock movements linked
     * to the order (when available).</p>
     *
     * @param id order id
     */
    @GetMapping("/ui/orders/{id}")
    public String orderDetail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var order = orderService.getOrderById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

            Sort itemSort = Sort.by(Sort.Direction.ASC, "id");
            Sort shipmentSort = Sort.by(Sort.Direction.DESC, "id");
            Sort movementSort = Sort.by(Sort.Direction.DESC, "dateTime").and(Sort.by(Sort.Direction.DESC, "id"));

            model.addAttribute("order", order);
            model.addAttribute("items", safeList(() -> orderItemService.getOrderItemsByOrderId(id, itemSort), model));
            model.addAttribute("shipments", safeList(() -> shipmentService.getShipmentsByOrderId(id, shipmentSort), model));
            model.addAttribute("movements", safeList(() -> movementService.getMovementsByOrderId(id, movementSort), model));
            return "ui/order-detail";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order not found.");
            return "redirect:/ui/orders";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/orders";
        }
    }

    /**
     * Order create form (draft mode).
     *
     * <p>This screen supports a simple “draft” experience: the order header and a list of
     * draft items are kept in the HTTP session until the final create action is submitted.</p>
     */
    @GetMapping("/ui/orders/new")
    public String newOrder(Model model, HttpSession session) {
        if (!model.containsAttribute("orderForm")) {
            CreateOrderRequest header = getOrInitDraftOrderHeader(session);
            model.addAttribute("orderForm", header);
        }

        if (!model.containsAttribute("draftItemForm")) {
            model.addAttribute("draftItemForm", new OrderDraftItemForm());
        }

        populateOrderReferenceData(model);
        addDraftOrderItemsToModel(session, model);

        model.addAttribute("formMode", "create");
        return "ui/order-form";
    }

    /**
     * Add an item to the in-session order draft.
     *
     * <p>Persists the current header into the session so the user doesn't lose changes when
     * adding/removing draft items before final submission.</p>
     */
    @PostMapping("/ui/orders/draft/items")
    public String addDraftOrderItem(
            @ModelAttribute("orderForm") CreateOrderRequest header,
            @Valid @ModelAttribute("draftItemForm") OrderDraftItemForm draftItemForm,
            BindingResult bindingResult,
            HttpSession session,
            Model model) {

        // Persist header values into session so user doesn't lose work when adding/removing items
        session.setAttribute(SESSION_ORDER_DRAFT_HEADER, header);

        if (bindingResult.hasErrors()) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            model.addAttribute("formMode", "create");
            return "ui/order-form";
        }

        try {
            int available = stockService.getTotalAvailableQuantity(draftItemForm.getResourceId());
            if (draftItemForm.getQuantity() > available) {
                populateOrderReferenceData(model);
                addDraftOrderItemsToModel(session, model);
                model.addAttribute("formMode", "create");
                model.addAttribute("errorMessage", "Not enough stock available for the requested quantity.");
                return "ui/order-form";
            }

            List<OrderDraftItem> draftItems = getOrInitDraftOrderItems(session);
            draftItems.add(new OrderDraftItem(draftItemForm.getResourceId(), draftItemForm.getQuantity()));
            session.setAttribute(SESSION_ORDER_DRAFT_ITEMS, draftItems);

            return "redirect:/ui/orders/new";
        } catch (DataAccessException ex) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't add this item right now. Please try again.");
            return "ui/order-form";
        }
    }

    /**
     * Remove an item from the in-session order draft.
     *
     * @param index zero-based index in the draft list
     */
    @PostMapping("/ui/orders/draft/items/{index}/remove")
    public String removeDraftOrderItem(
            @PathVariable int index,
            @ModelAttribute("orderForm") CreateOrderRequest header,
            HttpSession session) {

        session.setAttribute(SESSION_ORDER_DRAFT_HEADER, header);

        List<OrderDraftItem> draftItems = getOrInitDraftOrderItems(session);
        if (index >= 0 && index < draftItems.size()) {
            draftItems.remove(index);
        }
        session.setAttribute(SESSION_ORDER_DRAFT_ITEMS, draftItems);
        return "redirect:/ui/orders/new";
    }

    /**
     * Create the order and its items.
     *
     * <p>Uses the items stored in the session draft. On success, clears the draft from the
     * session.</p>
     */
    @PostMapping("/ui/orders")
    public String createOrder(
            @Valid @ModelAttribute("orderForm") CreateOrderRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model,
            HttpSession session) {

        if (bindingResult.hasErrors()) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            if (!model.containsAttribute("draftItemForm")) {
                model.addAttribute("draftItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("formMode", "create");
            return "ui/order-form";
        }

        try {
            List<CreateOrderItemRequest> items = getOrInitDraftOrderItems(session)
                    .stream()
                    .map(d -> new CreateOrderItemRequest(null, d.getResourceId(), d.getQuantity()))
                    .toList();

            orderService.createOrderWithItems(form, items);

            clearDraftOrder(session);
            redirectAttributes.addFlashAttribute("successMessage", "Order created successfully.");
            return "redirect:/ui/orders";
        } catch (InvalidRequestException ex) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            if (!model.containsAttribute("draftItemForm")) {
                model.addAttribute("draftItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/order-form";
        } catch (InsufficientStockException ex) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            if (!model.containsAttribute("draftItemForm")) {
                model.addAttribute("draftItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "Not enough stock available for one or more items.");
            return "ui/order-form";
        } catch (DataIntegrityViolationException ex) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            if (!model.containsAttribute("draftItemForm")) {
                model.addAttribute("draftItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "Please verify the selected unit.");
            return "ui/order-form";
        } catch (DataAccessException ex) {
            populateOrderReferenceData(model);
            addDraftOrderItemsToModel(session, model);
            if (!model.containsAttribute("draftItemForm")) {
                model.addAttribute("draftItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/order-form";
        }
    }

    private CreateOrderRequest getOrInitDraftOrderHeader(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ORDER_DRAFT_HEADER);
        if (existing instanceof CreateOrderRequest header) {
            if (header.getDateCreated() == null) {
                header.setDateCreated(LocalDate.now());
            }
            if (header.getStatus() == null || header.getStatus().isBlank()) {
                header.setStatus("CREATED");
            }
            return header;
        }

        CreateOrderRequest header = new CreateOrderRequest();
        header.setDateCreated(LocalDate.now());
        header.setStatus("CREATED");
        session.setAttribute(SESSION_ORDER_DRAFT_HEADER, header);
        return header;
    }

    @SuppressWarnings("unchecked")
    private List<OrderDraftItem> getOrInitDraftOrderItems(HttpSession session) {
        Object existing = session.getAttribute(SESSION_ORDER_DRAFT_ITEMS);
        if (existing instanceof List<?> list) {
            try {
                return (List<OrderDraftItem>) list;
            } catch (ClassCastException ex) {
                // fall through
            }
        }
        List<OrderDraftItem> draft = new ArrayList<>();
        session.setAttribute(SESSION_ORDER_DRAFT_ITEMS, draft);
        return draft;
    }

    private void clearDraftOrder(HttpSession session) {
        session.removeAttribute(SESSION_ORDER_DRAFT_ITEMS);
        session.removeAttribute(SESSION_ORDER_DRAFT_HEADER);
    }

    private void addDraftOrderItemsToModel(HttpSession session, Model model) {
        List<OrderDraftItem> draftItems = getOrInitDraftOrderItems(session);
        Sort resourceSort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        var resources = safeList(() -> resourceService.getAllResources(resourceSort), model);

        // also used by the draft "Add item" dropdown
        model.addAttribute("resources", resources);

        List<OrderDraftItemView> view = new ArrayList<>();
        for (int i = 0; i < draftItems.size(); i++) {
            OrderDraftItem item = draftItems.get(i);
            var resolved = resources.stream()
                    .filter(r -> r.getId() != null && r.getId().equals(item.getResourceId()))
                    .findFirst()
                    .orElse(null);

            String name = resolved != null ? resolved.getName() : "—";
            String type = resolved != null ? resolved.getType() : null;
            view.add(new OrderDraftItemView(i, item.getResourceId(), name, type, item.getQuantity()));
        }

        model.addAttribute("draftItems", view);
    }

    /**
     * Order edit form.
     *
     * <p>Edits the order header and lists existing items. Items can be managed inline via
     * dedicated endpoints on this controller.</p>
     *
     * @param id order id
     */
    @GetMapping("/ui/orders/{id}/edit")
    public String editOrder(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var order = orderService.getOrderById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

            if (!model.containsAttribute("orderForm")) {
                CreateOrderRequest form = new CreateOrderRequest();
                if (order.getUnit() != null) {
                    form.setUnitId(order.getUnit().getId());
                }
                form.setDateCreated(order.getDateCreated());
                form.setStatus(order.getStatus() != null ? order.getStatus().name() : null);
                model.addAttribute("orderForm", form);
            }

            populateOrderReferenceData(model);
            populateOrderItemReferenceData(model);
            model.addAttribute("orderItems", safeList(() -> orderItemService.getOrderItemsByOrderId(id, Sort.by(Sort.Direction.ASC, "id")), model));
            if (!model.containsAttribute("inlineItemForm")) {
                model.addAttribute("inlineItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("orderId", id);
            model.addAttribute("formMode", "edit");
            return "ui/order-form";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order not found.");
            return "redirect:/ui/orders";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/orders";
        }
    }

    /**
     * Update order header (unit/date/status).
     *
     * @param id order id
     */
    @PostMapping("/ui/orders/{id}")
    public String updateOrder(
            @PathVariable Long id,
            @Valid @ModelAttribute("orderForm") CreateOrderRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            populateOrderReferenceData(model);
            populateOrderItemReferenceData(model);
            model.addAttribute("orderItems", safeList(() -> orderItemService.getOrderItemsByOrderId(id, Sort.by(Sort.Direction.ASC, "id")), model));
            if (!model.containsAttribute("inlineItemForm")) {
                model.addAttribute("inlineItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("orderId", id);
            model.addAttribute("formMode", "edit");
            return "ui/order-form";
        }

        try {
            UpdateOrderRequest request = new UpdateOrderRequest();
            request.setUnitId(form.getUnitId());
            request.setDateCreated(form.getDateCreated());
            request.setStatus(form.getStatus());

            orderService.updateOrder(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Order updated successfully.");
            return "redirect:/ui/orders";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order not found.");
            return "redirect:/ui/orders";
        } catch (InvalidRequestException ex) {
            populateOrderReferenceData(model);
            populateOrderItemReferenceData(model);
            model.addAttribute("orderItems", safeList(() -> orderItemService.getOrderItemsByOrderId(id, Sort.by(Sort.Direction.ASC, "id")), model));
            if (!model.containsAttribute("inlineItemForm")) {
                model.addAttribute("inlineItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("orderId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/order-form";
        } catch (DataIntegrityViolationException ex) {
            populateOrderReferenceData(model);
            populateOrderItemReferenceData(model);
            model.addAttribute("orderItems", safeList(() -> orderItemService.getOrderItemsByOrderId(id, Sort.by(Sort.Direction.ASC, "id")), model));
            if (!model.containsAttribute("inlineItemForm")) {
                model.addAttribute("inlineItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("orderId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "Please verify the selected unit.");
            return "ui/order-form";
        } catch (DataAccessException ex) {
            populateOrderReferenceData(model);
            populateOrderItemReferenceData(model);
            model.addAttribute("orderItems", safeList(() -> orderItemService.getOrderItemsByOrderId(id, Sort.by(Sort.Direction.ASC, "id")), model));
            if (!model.containsAttribute("inlineItemForm")) {
                model.addAttribute("inlineItemForm", new OrderDraftItemForm());
            }
            model.addAttribute("orderId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/order-form";
        }
    }

    /**
     * Add a new item to an existing order from the edit screen.
     *
     * <p>This is separate from the “draft” create flow; it creates a persisted order item.</p>
     */
    @PostMapping("/ui/orders/{orderId}/items/inline-add")
    public String addOrderItemInline(
            @PathVariable Long orderId,
            @Valid @ModelAttribute("inlineItemForm") OrderDraftItemForm inlineItemForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            try {
                var order = orderService.getOrderById(orderId)
                        .orElseThrow(() -> new ResourceNotFoundException("Order", "id", orderId));

                CreateOrderRequest orderForm = new CreateOrderRequest();
                if (order.getUnit() != null) {
                    orderForm.setUnitId(order.getUnit().getId());
                }
                orderForm.setDateCreated(order.getDateCreated());
                orderForm.setStatus(order.getStatus() != null ? order.getStatus().name() : null);
                model.addAttribute("orderForm", orderForm);

                populateOrderReferenceData(model);
                populateOrderItemReferenceData(model);
                model.addAttribute("orderItems", safeList(() -> orderItemService.getOrderItemsByOrderId(orderId, Sort.by(Sort.Direction.ASC, "id")), model));
                model.addAttribute("orderId", orderId);
                model.addAttribute("formMode", "edit");
                model.addAttribute("errorMessage", "Please correct the item fields.");
                return "ui/order-form";
            } catch (DataAccessException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
                return "redirect:/ui/orders/" + orderId + "/edit";
            }
        }

        try {
            orderItemService.createOrderItem(new CreateOrderItemRequest(orderId, inlineItemForm.getResourceId(), inlineItemForm.getQuantity()));
            redirectAttributes.addFlashAttribute("successMessage", "Item added to the order.");
        } catch (InsufficientStockException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Not enough stock available for the requested quantity.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Please verify the selected resource.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }

        return "redirect:/ui/orders/" + orderId + "/edit";
    }

    /**
     * Update an existing order item quantity from the edit screen.
     *
     * @param orderId order id (used for redirect and ownership check)
     * @param itemId order item id
     */
    @PostMapping("/ui/orders/{orderId}/items/{itemId}/inline-update")
    public String updateOrderItemInline(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            @RequestParam("quantity") int quantity,
            RedirectAttributes redirectAttributes) {

        try {
            var item = orderItemService.getOrderItemById(itemId)
                    .orElseThrow(() -> new ResourceNotFoundException("OrderItem", "id", itemId));

            if (item.getOrder() == null || item.getOrder().getId() == null || !item.getOrder().getId().equals(orderId)) {
                redirectAttributes.addFlashAttribute("errorMessage", "This item does not belong to the selected order.");
                return "redirect:/ui/orders/" + orderId + "/edit";
            }

            UpdateOrderItemRequest request = new UpdateOrderItemRequest();
            request.setOrderId(orderId);
            request.setQuantity(quantity);
            orderItemService.updateOrderItem(itemId, request);

            redirectAttributes.addFlashAttribute("successMessage", "Item updated.");
        } catch (InsufficientStockException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Not enough stock available for the requested quantity.");
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order item not found.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }

        return "redirect:/ui/orders/" + orderId + "/edit";
    }

    /**
     * Remove an order item from an existing order.
     *
     * @param orderId order id (used for redirect)
     * @param itemId item id
     */
    @PostMapping("/ui/orders/{orderId}/items/{itemId}/inline-delete")
    public String deleteOrderItemInline(
            @PathVariable Long orderId,
            @PathVariable Long itemId,
            RedirectAttributes redirectAttributes) {

        try {
            orderItemService.deleteOrderItem(itemId);
            redirectAttributes.addFlashAttribute("successMessage", "Item removed from the order.");
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order item not found.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "This item is in use and cannot be deleted.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }

        return "redirect:/ui/orders/" + orderId + "/edit";
    }

    /**
     * Delete an order.
     *
     * @param id order id
     */
    @PostMapping("/ui/orders/{id}/delete")
    public String deleteOrder(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            orderService.deleteOrder(id);
            redirectAttributes.addFlashAttribute("successMessage", "Order deleted successfully.");
        } catch (InvalidRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Order not found.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete this order because it is referenced by other records.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/orders";
    }

    /**
     * Movements list page.
     *
     * <p>Supports sorting via query parameters and uses safe accessors to avoid hard failures
     * when the database is temporarily unavailable.</p>
     */
    @GetMapping("/ui/movements")
    public String movements(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "date" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "desc" : dir.trim().toLowerCase();

        Sort.Direction direction = "asc".equals(dirValue) ? Sort.Direction.ASC : Sort.Direction.DESC;

        String property = switch (sortKey) {
            case "resource" -> "stock.resource.name";
            case "warehouse" -> "stock.warehouse.name";
            case "action" -> "type";
            case "amount" -> "quantity";
            case "stock" -> "stock.id";
            case "date" -> "dateTime";
            default -> "dateTime";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.DESC, "id"));

        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("movements", safeList(() -> movementService.getAllMovements(sorting), model));
        return "ui/movements";
    }

    /**
     * Stocks list page.
     *
     * <p>Supports sorting via query parameters.</p>
     */
    @GetMapping("/ui/stocks")
    public String stocks(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "id" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "asc" : dir.trim().toLowerCase();
        Sort.Direction direction = "desc".equals(dirValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "warehouse" -> "warehouse.name";
            case "quantity" -> "quantity";
            case "resource" -> "resource.name";
            default -> "resource.name";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("stocks", safeList(() -> stockService.getAllStocks(sorting), model));
        return "ui/stocks";
    }

    /**
     * Stock create form.
     */
    @GetMapping("/ui/stocks/new")
    public String newStock(Model model) {
        if (!model.containsAttribute("stockForm")) {
            model.addAttribute("stockForm", new CreateStockRequest());
        }
        model.addAttribute("warehouses", safeList(() -> warehouseService.getAllWarehouses(), model));
        model.addAttribute("resources", safeList(() -> resourceService.getAllResources(), model));
        return "ui/stock-form";
    }

    /**
     * Create a new stock record.
     *
     * <p>Rejects duplicates (resource + warehouse) and directs users to the adjust action
     * for existing records.</p>
     */
    @PostMapping("/ui/stocks")
    public String createStock(
            @Valid @ModelAttribute("stockForm") CreateStockRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("warehouses", safeList(() -> warehouseService.getAllWarehouses(), model));
            model.addAttribute("resources", safeList(() -> resourceService.getAllResources(), model));
            return "ui/stock-form";
        }

        try {
            var existing = stockService.getStockByResourceAndWarehouse(form.getResourceId(), form.getWarehouseId());
            if (existing.isPresent()) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "This resource already has a stock entry in the selected warehouse. Use Adjust on the existing entry to change quantity."
                );
                return "redirect:/ui/stocks";
            }

            stockService.createStock(form);
            redirectAttributes.addFlashAttribute("successMessage", "Stock record created successfully.");
            return "redirect:/ui/stocks";
        } catch (InvalidRequestException ex) {
            model.addAttribute("warehouses", safeList(() -> warehouseService.getAllWarehouses(), model));
            model.addAttribute("resources", safeList(() -> resourceService.getAllResources(), model));
            model.addAttribute("errorMessage", ex.getMessage());
            return "ui/stock-form";
        } catch (DataAccessException ex) {
            model.addAttribute("warehouses", safeList(() -> warehouseService.getAllWarehouses(), model));
            model.addAttribute("resources", safeList(() -> resourceService.getAllResources(), model));
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/stock-form";
        }
    }

    /**
     * Stock adjust form.
     *
     * @param id stock id
     */
    @GetMapping("/ui/stocks/{id}/adjust")
    public String adjustStockPage(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var stock = stockService.getStockById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Stock", "id", id));

            if (!model.containsAttribute("adjustForm")) {
                model.addAttribute("adjustForm", new StockAdjustForm(StockAdjustForm.Operation.INCREASE, null));
            }
            model.addAttribute("stock", stock);
            return "ui/stock-adjust";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Stock record not found.");
            return "redirect:/ui/stocks";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/stocks";
        }
    }

    /**
     * Apply a stock adjustment (increase or decrease).
     *
     * <p>On a decrease, validates that stock cannot become negative.</p>
     *
     * @param id stock id
     */
    @PostMapping("/ui/stocks/{id}/adjust")
    public String adjustStock(
            @PathVariable Long id,
            @Valid @ModelAttribute("adjustForm") StockAdjustForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        var stock = stockService.getStockById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stock", "id", id));

        if (bindingResult.hasErrors()) {
            model.addAttribute("stock", stock);
            return "ui/stock-adjust";
        }

        try {
            int amount = form.getAmount();
            int delta = form.getOperation() == StockAdjustForm.Operation.DECREASE ? -amount : amount;

            stockService.adjustStock(id, new AdjustStockRequest(delta));
            redirectAttributes.addFlashAttribute("successMessage", "Stock updated successfully.");
            return "redirect:/ui/stocks";
        } catch (InsufficientStockException | InvalidRequestException ex) {
            model.addAttribute("stock", stock);

            if (ex instanceof InsufficientStockException) {
                model.addAttribute(
                        "errorMessage",
                        "Not enough stock to decrease by " + form.getAmount() + ". Current quantity is " + stock.getQuantity() + "."
                );
            } else {
                model.addAttribute("errorMessage", "Unable to apply this change. Please review the values and try again.");
            }
            return "ui/stock-adjust";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Stock record not found.");
            return "redirect:/ui/stocks";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
            return "redirect:/ui/stocks";
        }
    }

    /**
     * Delete a stock record.
     *
     * @param id stock id
     */
    @PostMapping("/ui/stocks/{id}/delete")
    public String deleteStock(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            stockService.deleteStock(id);
            redirectAttributes.addFlashAttribute("successMessage", "Stock record deleted successfully.");
        } catch (InvalidRequestException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Stock record not found.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete stock because it is referenced by other records.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/stocks";
    }

    /**
     * Units list page.
     *
     * <p>Supports sorting via query parameters.</p>
     */
    @GetMapping("/ui/units")
    public String units(
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            Model model) {

        String sortKey = (sort == null || sort.isBlank()) ? "id" : sort.trim().toLowerCase();
        String dirValue = (dir == null || dir.isBlank()) ? "asc" : dir.trim().toLowerCase();
        Sort.Direction direction = "desc".equals(dirValue) ? Sort.Direction.DESC : Sort.Direction.ASC;

        String property = switch (sortKey) {
            case "id" -> "id";
            case "location" -> "location";
            case "name" -> "name";
            default -> "name";
        };

        Sort sorting = Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("sortKey", sortKey);
        model.addAttribute("sortDir", direction == Sort.Direction.ASC ? "asc" : "desc");

        model.addAttribute("units", safeList(() -> unitService.getAllUnits(sorting), model));
        return "ui/units";
    }

    /**
     * Unit create form.
     */
    @GetMapping("/ui/units/new")
    public String newUnit(Model model) {
        if (!model.containsAttribute("unitForm")) {
            model.addAttribute("unitForm", new CreateUnitRequest());
        }
        model.addAttribute("formMode", "create");
        return "ui/unit-form";
    }

    /**
     * Create unit action.
     */
    @PostMapping("/ui/units")
    public String createUnit(
            @Valid @ModelAttribute("unitForm") CreateUnitRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("formMode", "create");
            return "ui/unit-form";
        }

        try {
            unitService.createUnit(form);
            redirectAttributes.addFlashAttribute("successMessage", "Unit created successfully.");
            return "redirect:/ui/units";
        } catch (DataAccessException ex) {
            model.addAttribute("formMode", "create");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/unit-form";
        }
    }

    /**
     * Unit edit form.
     *
     * @param id unit id
     */
    @GetMapping("/ui/units/{id}/edit")
    public String editUnit(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        try {
            var unit = unitService.getUnitById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", "id", id));

            if (!model.containsAttribute("unitForm")) {
                CreateUnitRequest form = new CreateUnitRequest();
                form.setName(unit.getName());
                form.setLocation(unit.getLocation());
                form.setLatitude(unit.getLatitude());
                form.setLongitude(unit.getLongitude());
                model.addAttribute("unitForm", form);
            }
            model.addAttribute("unitId", id);
            model.addAttribute("formMode", "edit");
            return "ui/unit-form";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unit not found.");
            return "redirect:/ui/units";
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't load this information right now. Please try again.");
            return "redirect:/ui/units";
        }
    }

    /**
     * Update an existing unit.
     *
     * @param id unit id
     */
    @PostMapping("/ui/units/{id}")
    public String updateUnit(
            @PathVariable Long id,
            @Valid @ModelAttribute("unitForm") CreateUnitRequest form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("unitId", id);
            model.addAttribute("formMode", "edit");
            return "ui/unit-form";
        }

        try {
            UpdateUnitRequest request = new UpdateUnitRequest();
            request.setName(form.getName());
            request.setLocation(form.getLocation());
            request.setLatitude(form.getLatitude());
            request.setLongitude(form.getLongitude());

            unitService.updateUnit(id, request);
            redirectAttributes.addFlashAttribute("successMessage", "Unit updated successfully.");
            return "redirect:/ui/units";
        } catch (ResourceNotFoundException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Unit not found.");
            return "redirect:/ui/units";
        } catch (DataAccessException ex) {
            model.addAttribute("unitId", id);
            model.addAttribute("formMode", "edit");
            model.addAttribute("errorMessage", "We couldn't save your changes right now. Please try again.");
            return "ui/unit-form";
        }
    }

    /**
     * Delete a unit.
     *
     * @param id unit id
     */
    @PostMapping("/ui/units/{id}/delete")
    public String deleteUnit(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            unitService.deleteUnit(id);
            redirectAttributes.addFlashAttribute("successMessage", "Unit deleted successfully.");
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Cannot delete unit because it is referenced by other records.");
        } catch (DataAccessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "We couldn't complete that action right now. Please try again.");
        }
        return "redirect:/ui/units";
    }

    private void populateShipmentReferenceData(Model model) {
        Sort orderSort = Sort.by(Sort.Direction.DESC, "dateCreated").and(Sort.by(Sort.Direction.DESC, "id"));
        Sort vehicleSort = Sort.by(Sort.Direction.ASC, "id");
        Sort warehouseSort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));

        model.addAttribute("orders", safeList(() -> orderService.getOpenOrders(orderSort), model));
        model.addAttribute("vehicles", safeList(() -> vehicleService.getAllVehicles(vehicleSort), model));
        model.addAttribute("warehouses", safeList(() -> warehouseService.getAllWarehouses(warehouseSort), model));
        model.addAttribute("shipmentStatuses",
            java.util.Arrays.stream(ShipmentStatus.values()).map(Enum::name).toList());
    }

    private void populateOrderReferenceData(Model model) {
        Sort unitSort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("units", safeList(() -> unitService.getAllUnits(unitSort), model));
        model.addAttribute("orderStatuses",
            java.util.Arrays.stream(OrderStatus.values()).map(Enum::name).toList());
    }

    private void populateOrderItemReferenceData(Model model) {
        Sort resourceSort = Sort.by(Sort.Direction.ASC, "name").and(Sort.by(Sort.Direction.ASC, "id"));
        model.addAttribute("resources", safeList(() -> resourceService.getAllResources(resourceSort), model));
    }

    private int safeCount(CountSupplier supplier, Model model) {
        try {
            return supplier.get();
        } catch (DataAccessException ex) {
            model.addAttribute("dataError", "Data is temporarily unavailable. Please try again in a moment.");
            return 0;
        }
    }

    private String normalizeVehicleType(String rawType) {
        if (rawType == null) {
            return null;
        }
        String value = rawType.trim().toUpperCase();
        return switch (value) {
            // canonical
            case "LAND", "SEA", "AIR" -> value;

            // legacy Spanish
            case "TERRESTRE" -> "LAND";
            case "MARITIMO" -> "SEA";
            case "AEREO" -> "AIR";

            // other common English variants
            case "TERRESTRIAL", "GROUND" -> "LAND";
            case "MARITIME" -> "SEA";
            case "AERIAL" -> "AIR";

            default -> rawType;
        };
    }

    private <T> List<T> safeList(ListSupplier<T> supplier, Model model) {
        try {
            return supplier.get();
        } catch (DataAccessException ex) {
            model.addAttribute("dataError", "Data is temporarily unavailable. Please try again in a moment.");
            return List.of();
        }
    }

    private long safeLong(LongSupplier supplier, Model model) {
        try {
            return supplier.get();
        } catch (DataAccessException ex) {
            model.addAttribute("dataError", "Data is temporarily unavailable. Please try again in a moment.");
            return 0L;
        }
    }

    private double safeDouble(DoubleSupplier supplier, Model model) {
        try {
            return supplier.get();
        } catch (DataAccessException ex) {
            model.addAttribute("dataError", "Data is temporarily unavailable. Please try again in a moment.");
            return 0.0;
        }
    }

    private <K, V> Map<K, V> safeMap(MapSupplier<K, V> supplier, Model model) {
        try {
            return supplier.get();
        } catch (DataAccessException ex) {
            model.addAttribute("dataError", "Data is temporarily unavailable. Please try again in a moment.");
            return Map.of();
        }
    }

    @FunctionalInterface
    private interface ListSupplier<T> {
        List<T> get();
    }

    @FunctionalInterface
    private interface CountSupplier {
        int get();
    }

    @FunctionalInterface
    private interface LongSupplier {
        long get();
    }

    @FunctionalInterface
    private interface DoubleSupplier {
        double get();
    }

    @FunctionalInterface
    private interface MapSupplier<K, V> {
        Map<K, V> get();
    }
}
