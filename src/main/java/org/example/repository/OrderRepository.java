package org.example.repository;
import org.example.domain.Models.RepairOrder;
import org.springframework.stereotype.Repository;
@Repository
public class OrderRepository extends MemoryRepository<RepairOrder> {}
