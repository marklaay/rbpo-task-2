package org.example.repository;
import org.example.domain.Models.RepairItem;
import org.springframework.stereotype.Repository;
@Repository
public class ItemRepository extends MemoryRepository<RepairItem> {}
