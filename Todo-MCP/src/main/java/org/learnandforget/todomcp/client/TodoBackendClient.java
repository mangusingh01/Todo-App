package org.learnandforget.todomcp.client;

import lombok.extern.slf4j.Slf4j;
import org.learnandforget.todomcp.dto.McpTodoDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;

/**
 * HTTP client that calls the Todo backend REST API.
 *
 * All MCP tools delegate to this client — they never talk to the
 * database directly. This keeps the MCP server stateless and thin.
 *
 * Auth: every request carries the JWT token configured in application.yml.
 * In a real deployment, the MCP server would obtain this token by calling
 * /api/auth/login on startup and refreshing it before expiry.
 */
@Component
@Slf4j
public class TodoBackendClient {

    private final WebClient webClient;

    public TodoBackendClient(
            @Value("${todo.backend.base-url}") String baseUrl,
            @Value("${todo.backend.jwt-token}") String jwtToken) {

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ── GET all todos (optionally filtered by status) ─────────────
    public List<McpTodoDto.TodoResponse> getTodos(String status) {
        String uri = status != null
                ? "/api/todos?status=" + status.toUpperCase()
                : "/api/todos";
        try {
            return webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToFlux(McpTodoDto.TodoResponse.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch todos: {} {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("Failed to fetch todos: " + e.getMessage());
        }
    }

    // ── GET overdue todos ─────────────────────────────────────────
    public List<McpTodoDto.TodoResponse> getOverdueTodos() {
        try {
            return webClient.get()
                    .uri("/api/todos/overdue")
                    .retrieve()
                    .bodyToFlux(McpTodoDto.TodoResponse.class)
                    .collectList()
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch overdue todos: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch overdue todos: " + e.getMessage());
        }
    }

    // ── GET summary ───────────────────────────────────────────────
    public McpTodoDto.SummaryResponse getSummary() {
        try {
            return webClient.get()
                    .uri("/api/todos/summary")
                    .retrieve()
                    .bodyToMono(McpTodoDto.SummaryResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to fetch summary: {}", e.getMessage());
            throw new RuntimeException("Failed to fetch summary: " + e.getMessage());
        }
    }

    // ── POST create todo ──────────────────────────────────────────
    public McpTodoDto.TodoResponse createTodo(McpTodoDto.CreateRequest request) {
        try {
            return webClient.post()
                    .uri("/api/todos")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(McpTodoDto.TodoResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to create todo: {}", e.getMessage());
            throw new RuntimeException("Failed to create todo: " + e.getMessage());
        }
    }

    // ── PUT update status ─────────────────────────────────────────
    public McpTodoDto.TodoResponse updateStatus(Long todoId, String status) {
        try {
            McpTodoDto.UpdateRequest request = new McpTodoDto.UpdateRequest();
            request.setStatus(status.toUpperCase());

            return webClient.put()
                    .uri("/api/todos/" + todoId)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(McpTodoDto.TodoResponse.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Failed to update todo {}: {}", todoId, e.getMessage());
            throw new RuntimeException("Failed to update todo: " + e.getMessage());
        }
    }
}