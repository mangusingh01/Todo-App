package org.learnandforget.todobackend.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.learnandforget.todobackend.dto.TodoDto;
import org.learnandforget.todobackend.model.Todo;
import org.learnandforget.todobackend.model.User;
import org.learnandforget.todobackend.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * All endpoints require a valid JWT — enforced by SecurityConfig.
 * @AuthenticationPrincipal injects the authenticated User directly
 * from SecurityContext — no manual token parsing needed here.
 */
@RestController
@RequestMapping("/api/todos")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;

    // GET /api/todos — all todos for authenticated user
    @GetMapping
    public ResponseEntity<List<TodoDto.Response>> getTodos(
            @AuthenticationPrincipal User user,
            @RequestParam(required = false) Todo.Status status) {

        if (status != null) {
            return ResponseEntity.ok(todoService.getTodosByStatus(user.getId(), status));
        }
        return ResponseEntity.ok(todoService.getTodosForUser(user.getId()));
    }

    // GET /api/todos/overdue — todos past due date, not done
    @GetMapping("/overdue")
    public ResponseEntity<List<TodoDto.Response>> getOverdue(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(todoService.getOverdueTodos(user.getId()));
    }

    // GET /api/todos/summary — count by status (used by MCP get_summary tool)
    @GetMapping("/summary")
    public ResponseEntity<TodoDto.SummaryResponse> getSummary(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(todoService.getSummary(user.getId()));
    }

    // POST /api/todos — create new todo
    @PostMapping
    public ResponseEntity<TodoDto.Response> createTodo(
            @Valid @RequestBody TodoDto.CreateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(todoService.createTodo(request, user));
    }

    // PUT /api/todos/{id} — update existing todo
    @PutMapping("/{id}")
    public ResponseEntity<TodoDto.Response> updateTodo(
            @PathVariable Long id,
            @Valid @RequestBody TodoDto.UpdateRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(todoService.updateTodo(id, request, user));
    }

    // DELETE /api/todos/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTodo(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        todoService.deleteTodo(id, user);
        return ResponseEntity.noContent().build();
    }
}