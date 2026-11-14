package org.example.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.domain.Models.Entity;

public abstract class MemoryRepository<T extends Entity> {
    private final Map<Long, T> data = new LinkedHashMap<>();
    private long lastId = 0;

    public long nextId() {
        lastId++;
        return lastId;
    }

    public List<T> findAll() {
        return List.copyOf(data.values());
    }

    public Optional<T> findById(long id) {
        return Optional.ofNullable(data.get(id));
    }

    public T save(T entity) {
        data.put(entity.id(), entity);
        return entity;
    }

    public void delete(long id) {
        data.remove(id);
    }
}
