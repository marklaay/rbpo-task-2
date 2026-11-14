package org.example.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.List;
import org.example.api.Commands.UserInput;
import org.example.domain.Models.User;
import org.example.service.RepairService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final RepairService service;
    private final RequestMapper mapper;

    public UserController(RepairService service, RequestMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<User> list() {
        return service.users();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable long id) {
        return service.user(id);
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody JsonNode body) {
        UserInput input = mapper.read(body, UserInput.class);
        User result = service.createUser(input);
        URI location = URI.create("/api/users/" + result.id());
        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping("/{id}")
    public User update(@PathVariable long id, @RequestBody JsonNode body) {
        UserInput input = mapper.read(body, UserInput.class);
        return service.updateUser(id, input);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
