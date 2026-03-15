package org.learnandforget.todobackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.learnandforget.todobackend.dto.TodoDto;
import org.learnandforget.todobackend.exception.TodoNotFoundException;
import org.learnandforget.todobackend.model.Todo;
import org.learnandforget.todobackend.model.User;
import org.learnandforget.todobackend.service.TodoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TodoController.class)
@DisplayName("TodoController")
class TodoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  TodoService todoService;

    private User testUser;
    private TodoDto.Response sampleResponse;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("mangu")
                .email("mangu@test.com")
                .password("encoded")
                .build();

        sampleResponse = TodoDto.Response.builder()
                .id(10L)
                .title("Write unit tests")
                .status(Todo.Status.PENDING)
                .priority(Todo.Priority.HIGH)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // ── GET /api/todos ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/todos")
    class GetTodos {

        @Test
        @DisplayName("returns 200 with todo list for authenticated user")
        void returns200_withTodoList() throws Exception {
            when(todoService.getTodosForUser(1L)).thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/todos")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(10))
                    .andExpect(jsonPath("$[0].title").value("Write unit tests"))
                    .andExpect(jsonPath("$[0].status").value("PENDING"));
        }

        @Test
        @DisplayName("returns 200 filtered by status when status param provided")
        void returns200_filteredByStatus() throws Exception {
            when(todoService.getTodosByStatus(1L, Todo.Status.PENDING))
                    .thenReturn(List.of(sampleResponse));

            mockMvc.perform(get("/api/todos")
                            .param("status", "PENDING")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].status").value("PENDING"));

            verify(todoService).getTodosByStatus(1L, Todo.Status.PENDING);
            verify(todoService, never()).getTodosForUser(any());
        }

        @Test
        @WithMockUser
        @DisplayName("returns 401 when unauthenticated")
        void returns401_whenUnauthenticated() throws Exception {
            mockMvc.perform(get("/api/todos"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ── POST /api/todos ───────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/todos")
    class CreateTodo {

        @Test
        @DisplayName("returns 201 with created todo")
        void returns201_withCreatedTodo() throws Exception {
            TodoDto.CreateRequest request = TodoDto.CreateRequest.builder()
                    .title("Write unit tests")
                    .priority(Todo.Priority.HIGH)
                    .build();

            when(todoService.createTodo(any(), any())).thenReturn(sampleResponse);

            mockMvc.perform(post("/api/todos")
                            .with(user(testUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10))
                    .andExpect(jsonPath("$.title").value("Write unit tests"));
        }

        @Test
        @DisplayName("returns 400 when title is blank")
        void returns400_whenTitleBlank() throws Exception {
            TodoDto.CreateRequest request = TodoDto.CreateRequest.builder()
                    .title("")   // violates @NotBlank
                    .build();

            mockMvc.perform(post("/api/todos")
                            .with(user(testUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.title").exists());
        }
    }

    // ── PUT /api/todos/{id} ───────────────────────────────────────

    @Nested
    @DisplayName("PUT /api/todos/{id}")
    class UpdateTodo {

        @Test
        @DisplayName("returns 200 with updated todo")
        void returns200_withUpdatedTodo() throws Exception {
            TodoDto.UpdateRequest request = TodoDto.UpdateRequest.builder()
                    .status(Todo.Status.DONE)
                    .build();

            when(todoService.updateTodo(eq(10L), any(), any()))
                    .thenReturn(sampleResponse);

            mockMvc.perform(put("/api/todos/10")
                            .with(user(testUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(10));
        }

        @Test
        @DisplayName("returns 404 when todo not found or not owned")
        void returns404_whenTodoNotFound() throws Exception {
            when(todoService.updateTodo(eq(99L), any(), any()))
                    .thenThrow(new TodoNotFoundException(99L));

            mockMvc.perform(put("/api/todos/99")
                            .with(user(testUser))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new TodoDto.UpdateRequest())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.message").value("Todo not found with id: 99"));
        }
    }

    // ── DELETE /api/todos/{id} ────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/todos/{id}")
    class DeleteTodo {

        @Test
        @DisplayName("returns 204 on successful delete")
        void returns204_onDelete() throws Exception {
            doNothing().when(todoService).deleteTodo(10L, testUser);

            mockMvc.perform(delete("/api/todos/10")
                            .with(user(testUser)))
                    .andExpect(status().isNoContent());

            verify(todoService).deleteTodo(10L, testUser);
        }

        @Test
        @DisplayName("returns 404 when todo not found")
        void returns404_whenTodoNotFound() throws Exception {
            doThrow(new TodoNotFoundException(99L))
                    .when(todoService).deleteTodo(99L, testUser);

            mockMvc.perform(delete("/api/todos/99")
                            .with(user(testUser)))
                    .andExpect(status().isNotFound());
        }
    }

    // ── GET /api/todos/summary ────────────────────────────────────

    @Nested
    @DisplayName("GET /api/todos/summary")
    class GetSummary {

        @Test
        @DisplayName("returns 200 with summary counts")
        void returns200_withSummary() throws Exception {
            TodoDto.SummaryResponse summary = TodoDto.SummaryResponse.builder()
                    .pending(3).inProgress(1).done(5).overdue(2)
                    .build();

            when(todoService.getSummary(1L)).thenReturn(summary);

            mockMvc.perform(get("/api/todos/summary")
                            .with(user(testUser)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pending").value(3))
                    .andExpect(jsonPath("$.done").value(5))
                    .andExpect(jsonPath("$.overdue").value(2));
        }
    }
}