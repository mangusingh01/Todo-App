package org.learnandforget.todomcp.tools;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.learnandforget.todomcp.client.TodoBackendClient;
import org.learnandforget.todomcp.dto.McpTodoDto;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MCP tool definitions.
 *
 * Each @Tool method is:
 *  - Auto-discovered by spring.ai.mcp.server.annotation-scanner
 *  - Exposed to Claude as a callable tool with auto-generated JSON schema
 *  - The description string IS what Claude reads to decide when to call the tool.
 *    Write descriptions as if explaining the tool to a smart developer — be precise.
 *
 * Tool naming convention: verb_noun (get_todos, create_todo, update_status)
 * This matches how Claude naturally thinks about operations.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TodoMcpTools {

    private final TodoBackendClient client;

    // ── Tool 1: get_todos ─────────────────────────────────────────

    @Tool(name = "get_todos",
            description = """
                  Fetches the user's todo items. Optionally filter by status.
                  Status values: PENDING, IN_PROGRESS, DONE.
                  Returns a formatted list of todos with id, title, status, priority, and due date.
                  Use this when the user asks to see, list, or show their tasks.
                  """)
    public String getTodos(
            @ToolParam(description = "Optional status filter: PENDING, IN_PROGRESS, or DONE. " +
                    "Leave null to fetch all todos.")
            String status) {

        log.info("MCP tool get_todos called [status={}]", status);
        List<McpTodoDto.TodoResponse> todos = client.getTodos(status);

        if (todos.isEmpty()) {
            return status != null
                    ? "No todos found with status: " + status
                    : "You have no todos yet.";
        }

        return formatTodoList(todos);
    }

    // ── Tool 2: get_overdue ───────────────────────────────────────

    @Tool(name = "get_overdue",
            description = """
                  Fetches todos that are past their due date and not yet completed (not DONE).
                  Returns todos ordered by due date ascending (most overdue first).
                  Use this when the user asks about late, overdue, or missed tasks.
                  """)
    public String getOverdue() {
        log.info("MCP tool get_overdue called");
        List<McpTodoDto.TodoResponse> overdue = client.getOverdueTodos();

        if (overdue.isEmpty()) {
            return "Great news — you have no overdue todos!";
        }

        return "You have " + overdue.size() + " overdue todo(s):\n\n"
                + formatTodoList(overdue);
    }

    // ── Tool 3: get_summary ───────────────────────────────────────

    @Tool(name = "get_summary",
            description = """
                  Returns a summary count of todos grouped by status: pending, in_progress, done, overdue.
                  Use this when the user asks for an overview, summary, or progress update on their tasks.
                  Also useful as a quick check before suggesting what to work on next.
                  """)
    public String getSummary() {
        log.info("MCP tool get_summary called");
        McpTodoDto.SummaryResponse summary = client.getSummary();

        return String.format("""
                📋 Todo Summary:
                  • Pending:     %d
                  • In Progress: %d
                  • Done:        %d
                  • Overdue:     %d
                  • Total:       %d
                """,
                summary.getPending(),
                summary.getInProgress(),
                summary.getDone(),
                summary.getOverdue(),
                summary.getPending() + summary.getInProgress() + summary.getDone()
        );
    }

    // ── Tool 4: create_todo ───────────────────────────────────────

    @Tool(name = "create_todo",
            description = """
                  Creates a new todo item for the user.
                  Required: title. Optional: description, priority (LOW/MEDIUM/HIGH), dueDate (YYYY-MM-DD).
                  Returns the created todo with its assigned id.
                  Use this when the user asks to add, create, or remember a task.
                  """)
    public String createTodo(
            @ToolParam(description = "Title of the todo — short and descriptive")
            String title,

            @ToolParam(description = "Optional longer description or notes")
            String description,

            @ToolParam(description = "Priority level: LOW, MEDIUM, or HIGH. Defaults to MEDIUM.")
            String priority,

            @ToolParam(description = "Optional due date in YYYY-MM-DD format")
            String dueDate) {

        log.info("MCP tool create_todo called [title={}, priority={}]", title, priority);

        McpTodoDto.CreateRequest request = McpTodoDto.CreateRequest.builder()
                .title(title)
                .description(description)
                .priority(priority != null ? priority.toUpperCase() : "MEDIUM")
                .dueDate(dueDate != null ? LocalDate.parse(dueDate) : null)
                .build();

        McpTodoDto.TodoResponse created = client.createTodo(request);

        return String.format(
                "✅ Created todo #%d: \"%s\" [%s priority%s]",
                created.getId(),
                created.getTitle(),
                created.getPriority(),
                created.getDueDate() != null ? ", due " + created.getDueDate() : ""
        );
    }

    // ── Tool 5: update_status ─────────────────────────────────────

    @Tool(name = "update_status",
            description = """
                  Updates the status of an existing todo by its id.
                  Status values: PENDING, IN_PROGRESS, DONE.
                  Use this when the user says they've completed, started, or want to reset a task.
                  If the user says "mark task X as done", use get_todos first to find the id, then call this.
                  """)
    public String updateStatus(
            @ToolParam(description = "The numeric id of the todo to update")
            Long todoId,

            @ToolParam(description = "New status: PENDING, IN_PROGRESS, or DONE")
            String status) {

        log.info("MCP tool update_status called [todoId={}, status={}]", todoId, status);

        McpTodoDto.TodoResponse updated = client.updateStatus(todoId, status);

        return String.format(
                "✅ Todo #%d \"%s\" marked as %s",
                updated.getId(),
                updated.getTitle(),
                updated.getStatus()
        );
    }

    // ── Formatting helper ─────────────────────────────────────────

    private String formatTodoList(List<McpTodoDto.TodoResponse> todos) {
        return todos.stream()
                .map(t -> String.format(
                        "#%d [%s] [%s] %s%s",
                        t.getId(),
                        t.getStatus(),
                        t.getPriority(),
                        t.getTitle(),
                        t.getDueDate() != null ? " (due: " + t.getDueDate() + ")" : ""
                ))
                .collect(Collectors.joining("\n"));
    }
}