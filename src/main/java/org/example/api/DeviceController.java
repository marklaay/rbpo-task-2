package org.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import org.example.api.Commands.DeviceInput;
import org.example.domain.Models.Device;
import org.example.service.RepairService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {
    private final RepairService service;
    private final RequestMapper mapper;

    public DeviceController(RepairService service, RequestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<Device> list() {
        return service.devices();
    }

    @GetMapping("/{id}")
    public Device get(@PathVariable long id) {
        return service.device(id);
    }

    @PostMapping
    public ResponseEntity<Device> create(@RequestBody JsonNode body) {
        DeviceInput input = mapper.read(body, DeviceInput.class);
        Device result = service.createDevice(input);
        URI location = URI.create("/api/devices/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping("/{id}")
    public Device update(@PathVariable long id, @RequestBody JsonNode body) {
        DeviceInput input = mapper.read(body, DeviceInput.class);
        return service.updateDevice(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }
}
