package org.learnandforget.todobackend.repository;

import org.learnandforget.todobackend.model.Todo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA repository — used only for single-record operations:
 * findById, save, delete.
 *
 * Bulk reads live in TodoRepository (JDBC) for performance.
 */
@Repository
public interface TodoJpaRepository extends JpaRepository<Todo, Long> {

    // Used to verify ownership before update/delete
    Optional<Todo> findByIdAndUserId(Long id, Long userId);
}