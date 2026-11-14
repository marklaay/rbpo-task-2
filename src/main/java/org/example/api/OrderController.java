package org.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import org.example.api.Commands.OrderInput;
import org.example.domain.Models.RepairOrder;
import org.example.service.RepairService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repair-orders")
public class OrderController {
    private final RepairService service;
    private final RequestMapper mapper;

    public OrderController(RepairService service, RequestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<RepairOrder> list() {
        return service.orders();
    }

    @GetMapping("/{id}")
    public RepairOrder get(@PathVariable long id) {
        return service.order(id);
    }

    @PostMapping
    public ResponseEntity<RepairOrder> create(@RequestBody JsonNode body) {
        OrderInput input = mapper.read(body, OrderInput.class);
        RepairOrder result = service.createOrder(input);
        URI location = URI.create("/api/repair-orders/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping("/{id}")
    public RepairOrder update(@PathVariable long id, @RequestBody JsonNode body) {
        OrderInput input = mapper.read(body, OrderInput.class);
        return service.updateOrder(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }
}
