package org.learnandforget.todomcp.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class McpTodoDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TodoResponse {
        private Long id;
        private String title;
        private String description;
        private String status;
        private String priority;
        private LocalDate dueDate;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CreateRequest {
        private String title;
        private String description;
        private String priority;
        private LocalDate dueDate;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class UpdateRequest {
        private String status;
        private String priority;
        private String title;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SummaryResponse {
        private long pending;
        private long inProgress;
        private long done;
        private long overdue;
    }
}