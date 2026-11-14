package org.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import org.example.api.Commands.PartInput;
import org.example.domain.Models.SparePart;
import org.example.service.RepairService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/spare-parts")
public class PartController {
    private final RepairService service;
    private final RequestMapper mapper;

    public PartController(RepairService service, RequestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<SparePart> list() {
        return service.parts();
    }

    @GetMapping("/{id}")
    public SparePart get(@PathVariable long id) {
        return service.part(id);
    }

    @PostMapping
    public ResponseEntity<SparePart> create(@RequestBody JsonNode body) {
        PartInput input = mapper.read(body, PartInput.class);
        SparePart result = service.createPart(input);
        URI location = URI.create("/api/spare-parts/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping("/{id}")
    public SparePart update(@PathVariable long id, @RequestBody JsonNode body) {
        PartInput input = mapper.read(body, PartInput.class);
        return service.updatePart(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deletePart(id);
        return ResponseEntity.noContent().build();
    }
}
