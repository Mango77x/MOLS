package com.mls.logistics.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Data Transfer Object for creating an Order together with its line items
 * in a single call.
 *
 * Mirrors the session-draft wizard flow of the Thymeleaf UI
 * ({@code /ui/orders/new}), but submitted as one atomic request: the order
 * and every item are created in a single transaction
 * (see {@link com.mls.logistics.service.OrderService#createOrderWithItems}),
 * so a stock conflict on any item leaves nothing persisted.
 */
public class CreateOrderWithItemsRequest {

    @NotNull(message = "Order header is required")
    @Valid
    private CreateOrderRequest header;

    private List<@Valid OrderItemLineRequest> items;

    public CreateOrderWithItemsRequest() {
    }

    public CreateOrderWithItemsRequest(CreateOrderRequest header, List<@Valid OrderItemLineRequest> items) {
        this.header = header;
        this.items = items;
    }

    public CreateOrderRequest getHeader() {
        return header;
    }

    public void setHeader(CreateOrderRequest header) {
        this.header = header;
    }

    public List<@Valid OrderItemLineRequest> getItems() {
        return items;
    }

    public void setItems(List<@Valid OrderItemLineRequest> items) {
        this.items = items;
    }
}
