# Todo MCP Server

MCP server that exposes Todo backend operations as LLM-callable tools.
Built with Spring AI MCP Server (Streamable HTTP transport).

## Tools exposed to Claude

| Tool | Description |
|------|-------------|
| `get_todos` | Fetch all todos, optionally filtered by status |
| `get_overdue` | Fetch todos past due date |
| `get_summary` | Count todos by status |
| `create_todo` | Create a new todo |
| `update_status` | Mark a todo as PENDING / IN_PROGRESS / DONE |

## Running locally

### 1. Start the Todo backend first
```bash
cd ../todo-backend
docker-compose up -d
```

### 2. Get a JWT token from the Todo backend
```bash
curl http://localhost:8080/actuator/health
```
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"usename","email":"user@test.com","password":"password123"}'
```
```bash
curl -X POST http://localhost:8080/api/todos \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <your_token>" \
  -d '{"title":"Test todo","priority":"HIGH"}'
```
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"youruser","password":"yourpassword"}'
```

Copy the `token` from the response.

### 3. Start the MCP server
```bash
export TODO_BACKEND_JWT=<your_jwt_token>
mvn spring-boot:run
```
```bash
curl http://localhost:8081/mcp
```

The MCP server starts on **http://localhost:8081**

### 4. Connect Claude Desktop
Copy `claude_desktop_config.json` content into your Claude Desktop config:

- **macOS**: `~/Library/Application Support/Claude/claude_desktop_config.json`
- **Windows**: `%APPDATA%\Claude\claude_desktop_config.json`

Restart Claude Desktop. You'll see the Todo tools appear in the tools panel.

## Example Claude prompts

Once connected, try these in Claude Desktop:

- *"Show me all my todos"*
- *"What tasks are overdue?"*
- *"Give me a summary of my todos"*
- *"Add a todo: Review pull requests, HIGH priority, due tomorrow"*
- *"Mark todo #3 as done"*
- *"What pending tasks do I have?"*

## Architecture

```
Claude Desktop
     │  MCP protocol (Streamable HTTP)
     ▼
Todo MCP Server :8081
     │  REST API calls (JWT auth)
     ▼
Todo Backend :8080
     │
     ├── MySQL (todos, users, audit_log)
     ├── Redis (todo list cache)
     └── Kafka (todo-events topic)
```
