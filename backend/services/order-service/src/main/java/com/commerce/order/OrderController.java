package com.commerce.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public OrderService.OrderView place(@RequestHeader("X-Internal-User-Id") String user,
                                       @RequestHeader(value="Idempotency-Key",required=false) String key,
                                       @Valid @RequestBody OrderService.PlaceOrder request) {
        return orders.place(user,key,request);
    }
    @GetMapping("/{id}")
    public OrderService.OrderView get(@PathVariable String id, @RequestHeader("X-Internal-User-Id") String user) {
        return orders.readOwned(id,user);
    }
}
