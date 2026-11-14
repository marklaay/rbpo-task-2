package org.example.repository;
import org.example.domain.Models.SparePart;
import org.springframework.stereotype.Repository;
@Repository
public class PartRepository extends MemoryRepository<SparePart> {}
