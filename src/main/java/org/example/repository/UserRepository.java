package org.example.repository;
import org.example.domain.Models.User;
import org.springframework.stereotype.Repository;
@Repository
public class UserRepository extends MemoryRepository<User> {}
