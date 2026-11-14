package org.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import org.example.api.Commands.ItemInput;
import org.example.domain.Models.RepairItem;
import org.example.service.RepairService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/repair-items")
public class ItemController {
    private final RepairService service;
    private final RequestMapper mapper;

    public ItemController(RepairService service, RequestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<RepairItem> list() {
        return service.items();
    }

    @GetMapping("/{id}")
    public RepairItem get(@PathVariable long id) {
        return service.item(id);
    }

    @PostMapping
    public ResponseEntity<RepairItem> create(@RequestBody JsonNode body) {
        ItemInput input = mapper.read(body, ItemInput.class);
        RepairItem result = service.createItem(input);
        URI location = URI.create("/api/repair-items/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping("/{id}")
    public RepairItem update(@PathVariable long id, @RequestBody JsonNode body) {
        ItemInput input = mapper.read(body, ItemInput.class);
        return service.updateItem(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteItem(id);
        return ResponseEntity.noContent().build();
    }
}
