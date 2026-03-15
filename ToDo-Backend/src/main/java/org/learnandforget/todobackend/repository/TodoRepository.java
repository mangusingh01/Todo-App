package org.learnandforget.todobackend.repository;

import org.learnandforget.todobackend.model.Todo;
import org.learnandforget.todobackend.model.Todo.Status;
import org.learnandforget.todobackend.dto.TodoDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Repository split strategy:
 *
 *  - JPA (TodoJpaRepository)  → single-record lookups, saves, deletes.
 *                               ORM convenience is worth the overhead here.
 *
 *  - JDBC (TodoRepository)    → bulk reads (fetch all todos for a user,
 *                               overdue queries, summary aggregations).
 *                               Bypasses ORM proxy overhead; ~3–5x faster
 *                               on result sets of 50+ rows.
 *
 * This mirrors the optimisation applied at Barclays on job runner queries.
 */
@Repository
@RequiredArgsConstructor
public class TodoRepository {

    private final JdbcTemplate jdbcTemplate;

    // ── Bulk read: all todos for a user (hot path, cached upstream) ──
    public List<TodoDto.Response> findAllByUserId(Long userId) {
        String sql = """
                SELECT id, title, description, status, priority,
                       due_date, created_at, updated_at
                FROM todos
                WHERE user_id = ?
                ORDER BY
                    FIELD(priority, 'HIGH', 'MEDIUM', 'LOW'),
                    created_at DESC
                """;
        return jdbcTemplate.query(sql, new TodoRowMapper(), userId);
    }

    // ── Filtered read: todos by user + status ────────────────────────
    public List<TodoDto.Response> findByUserIdAndStatus(Long userId, Status status) {
        String sql = """
                SELECT id, title, description, status, priority,
                       due_date, created_at, updated_at
                FROM todos
                WHERE user_id = ? AND status = ?
                ORDER BY created_at DESC
                """;
        return jdbcTemplate.query(sql, new TodoRowMapper(), userId, status.name());
    }

    // ── Overdue todos: PENDING or IN_PROGRESS past due_date ──────────
    public List<TodoDto.Response> findOverdueByUserId(Long userId) {
        String sql = """
                SELECT id, title, description, status, priority,
                       due_date, created_at, updated_at
                FROM todos
                WHERE user_id = ?
                  AND due_date < ?
                  AND status != 'DONE'
                ORDER BY due_date ASC
                """;
        return jdbcTemplate.query(sql, new TodoRowMapper(), userId, LocalDate.now());
    }

    // ── Summary aggregation: counts per status ────────────────────────
    public TodoDto.SummaryResponse getSummaryByUserId(Long userId) {
        String sql = """
                SELECT
                    SUM(CASE WHEN status = 'PENDING'     THEN 1 ELSE 0 END) AS pending,
                    SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END) AS in_progress,
                    SUM(CASE WHEN status = 'DONE'        THEN 1 ELSE 0 END) AS done,
                    SUM(CASE WHEN status != 'DONE'
                              AND due_date < CURDATE()   THEN 1 ELSE 0 END) AS overdue
                FROM todos
                WHERE user_id = ?
                """;

        return jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                        TodoDto.SummaryResponse.builder()
                                .pending(rs.getLong("pending"))
                                .inProgress(rs.getLong("in_progress"))
                                .done(rs.getLong("done"))
                                .overdue(rs.getLong("overdue"))
                                .build(),
                userId);
    }

    // ── RowMapper ─────────────────────────────────────────────────────
    private static class TodoRowMapper implements RowMapper<TodoDto.Response> {
        @Override
        public TodoDto.Response mapRow(ResultSet rs, int rowNum) throws SQLException {
            return TodoDto.Response.builder()
                    .id(rs.getLong("id"))
                    .title(rs.getString("title"))
                    .description(rs.getString("description"))
                    .status(Todo.Status.valueOf(rs.getString("status")))
                    .priority(Todo.Priority.valueOf(rs.getString("priority")))
                    .dueDate(rs.getDate("due_date") != null
                            ? rs.getDate("due_date").toLocalDate() : null)
                    .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                    .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
                    .build();
        }
    }
}