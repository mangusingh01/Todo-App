package org.learnandforget.todobackend.dto;

import org.learnandforget.todobackend.model.Todo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

// ── Inbound DTOs ──────────────────────────────────────────────────

public class TodoDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must be under 255 characters")
        private String title;

        private String description;

        private Todo.Priority priority;

        private LocalDate dueDate;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UpdateRequest {

        @Size(max = 255)
        private String title;

        private String description;

        private Todo.Status status;

        private Todo.Priority priority;

        private LocalDate dueDate;
    }

    // ── Outbound DTOs ─────────────────────────────────────────────

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Response {

        private Long id;
        private String title;
        private String description;
        private Todo.Status status;
        private Todo.Priority priority;
        private LocalDate dueDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Todo todo) {
            return Response.builder()
                    .id(todo.getId())
                    .title(todo.getTitle())
                    .description(todo.getDescription())
                    .status(todo.getStatus())
                    .priority(todo.getPriority())
                    .dueDate(todo.getDueDate())
                    .createdAt(todo.getCreatedAt())
                    .updatedAt(todo.getUpdatedAt())
                    .build();
        }
    }

    // Summary used by MCP server's get_summary tool
    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SummaryResponse {
        private long pending;
        private long inProgress;
        private long done;
        private long overdue;
    }
}