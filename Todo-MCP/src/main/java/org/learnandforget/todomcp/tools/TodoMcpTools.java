package org.learnandforget.todomcp.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learnandforget.todomcp.client.TodoBackendClient;
import org.learnandforget.todomcp.dto.McpTodoDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TodoMcpTools {

    private final TodoBackendClient client;

    @Tool(description = "Fetches the user's todo items. Optionally filter by status: PENDING, IN_PROGRESS, DONE. Returns list with id, title, status, priority, due date.")
    public String getTodos(
            @ToolParam(description = "Optional status filter: PENDING, IN_PROGRESS, or DONE. Pass null to fetch all.") String status) {
        log.info("MCP tool getTodos called [status={}]", status);
        List<McpTodoDto.TodoResponse> todos = client.getTodos(status);
        if (todos.isEmpty()) return status != null ? "No todos with status: " + status : "No todos yet.";
        return formatTodoList(todos);
    }

    @Tool(description = "Fetches todos that are past their due date and not completed. Ordered by due date ascending.")
    public String getOverdue() {
        log.info("MCP tool getOverdue called");
        List<McpTodoDto.TodoResponse> overdue = client.getOverdueTodos();
        if (overdue.isEmpty()) return "No overdue todos!";
        return "You have " + overdue.size() + " overdue todo(s):\n\n" + formatTodoList(overdue);
    }

    @Tool(description = "Returns a count of todos grouped by status: pending, in_progress, done, overdue.")
    public String getSummary() {
        log.info("MCP tool getSummary called");
        McpTodoDto.SummaryResponse s = client.getSummary();
        return String.format("Todo Summary:\n  Pending: %d\n  In Progress: %d\n  Done: %d\n  Overdue: %d\n  Total: %d",
                s.getPending(), s.getInProgress(), s.getDone(), s.getOverdue(),
                s.getPending() + s.getInProgress() + s.getDone());
    }

    @Tool(description = "Creates a new todo. Required: title. Optional: description, priority (LOW/MEDIUM/HIGH), dueDate (YYYY-MM-DD).")
    public String createTodo(
            @ToolParam(description = "Title of the todo") String title,
            @ToolParam(description = "Optional description") String description,
            @ToolParam(description = "Priority: LOW, MEDIUM, or HIGH") String priority,
            @ToolParam(description = "Due date in YYYY-MM-DD format") String dueDate) {
        log.info("MCP tool createTodo called [title={}]", title);
        McpTodoDto.CreateRequest request = McpTodoDto.CreateRequest.builder()
                .title(title).description(description)
                .priority(priority != null ? priority.toUpperCase() : "MEDIUM")
                .dueDate(dueDate != null ? LocalDate.parse(dueDate) : null)
                .build();
        McpTodoDto.TodoResponse created = client.createTodo(request);
        return String.format("Created todo #%d: \"%s\" [%s priority%s]",
                created.getId(), created.getTitle(), created.getPriority(),
                created.getDueDate() != null ? ", due " + created.getDueDate() : "");
    }

    @Tool(description = "Updates the status of a todo by id. Status: PENDING, IN_PROGRESS, or DONE. Use getTodos first to find the id.")
    public String updateStatus(
            @ToolParam(description = "Numeric id of the todo") Long todoId,
            @ToolParam(description = "New status: PENDING, IN_PROGRESS, or DONE") String status) {
        log.info("MCP tool updateStatus called [todoId={}, status={}]", todoId, status);
        McpTodoDto.TodoResponse updated = client.updateStatus(todoId, status);
        return String.format("Todo #%d \"%s\" marked as %s",
                updated.getId(), updated.getTitle(), updated.getStatus());
    }

    private String formatTodoList(List<McpTodoDto.TodoResponse> todos) {
        return todos.stream()
                .map(t -> String.format("#%d [%s] [%s] %s%s",
                        t.getId(), t.getStatus(), t.getPriority(), t.getTitle(),
                        t.getDueDate() != null ? " (due: " + t.getDueDate() + ")" : ""))
                .collect(Collectors.joining("\n"));
    }
}