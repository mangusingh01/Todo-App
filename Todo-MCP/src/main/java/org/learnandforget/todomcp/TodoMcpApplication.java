package org.learnandforget.todomcp;

import org.learnandforget.todomcp.tools.TodoMcpTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class TodoMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(TodoMcpApplication.class, args);
    }

    /**
     * Explicitly registers TodoMcpTools methods as MCP tools.
     * Spring AI scans @Tool annotations on this bean and exposes
     * them to Claude via the MCP protocol.
     */
    @Bean
    public ToolCallbackProvider todoTools(TodoMcpTools todoMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(todoMcpTools)
                .build();
    }
}