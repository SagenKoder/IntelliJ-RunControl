# IntelliJ RunControl

HTTP API for programmatically controlling IntelliJ IDEA run configurations.

Perfect for MCP servers, CLI tools, and automation scripts.

[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()
[![License](https://img.shields.io/badge/license-MIT-blue)]()
[![IntelliJ](https://img.shields.io/badge/IntelliJ-2024.1--2025.3-orange)]()

---

## Features

- 🚀 Control run configurations via HTTP (run, debug, stop, restart)
- 📊 Real-time status monitoring (idle, running, debugging, stopping, finished)
- 📝 Access logs with pagination, search, and tail
- 🔐 Token-based authentication, localhost-only
- 🏢 Multi-project support
- 🎯 Native IntelliJ Platform APIs

---

## Quick Start

### Installation

1. Download [IntelliJ-RunControl-1.0.2.zip](build/distributions/IntelliJ-RunControl-1.0.2.zip)
2. **Settings → Plugins → ⚙️ → Install Plugin from Disk**
3. Restart IntelliJ IDEA
4. **Settings → Tools → RunControl** - copy your API token

### Usage

```bash
TOKEN="your_token_here"

# List all projects
curl -H "X-IntelliJ-Token: $TOKEN" http://127.0.0.1:17777/projects

# List run configurations
curl -H "X-IntelliJ-Token: $TOKEN" http://127.0.0.1:17777/run-configs

# Start a configuration
curl -X POST -H "X-IntelliJ-Token: $TOKEN" \
  http://127.0.0.1:17777/run-configs/MyApp/run
```

---

## API Reference

All requests require header: `X-IntelliJ-Token: YOUR_TOKEN`

### Projects

- **`GET /projects`** - List all open projects

### Run Configurations

- **`GET /run-configs`** - List all run configurations (supports `?project=name`)
- **`GET /run-configs/{name}`** - Get specific configuration details
- **`POST /run-configs/{name}/run`** - Start the configuration
- **`POST /run-configs/{name}/debug`** - Start in debug mode
- **`POST /run-configs/{name}/stop`** - Stop the running process
- **`POST /run-configs/{name}/restart`** - Restart the configuration

### Logs

- **`GET /run-configs/{name}/logs`** - List available log sources (console, files)
- **`GET /run-configs/{name}/logs/{source}`** - Read log with pagination (`?offset=0&limit=100`)
- **`GET /run-configs/{name}/logs/{source}/tail`** - Get last N lines (`?lines=100`)
- **`GET /run-configs/{name}/logs/{source}/search`** - Search logs (`?q=ERROR&maxResults=50`)

All endpoints support `?project=name` for multi-project environments.

---

## Examples

### Start and Monitor Application

```bash
TOKEN="your_token"

# Start the app
curl -X POST -H "X-IntelliJ-Token: $TOKEN" \
  http://127.0.0.1:17777/run-configs/MySpringApp/run

# Check logs for errors
curl -H "X-IntelliJ-Token: $TOKEN" \
  "http://127.0.0.1:17777/run-configs/MySpringApp/logs/server.log/search?q=ERROR&maxResults=10"

# Get recent output
curl -H "X-IntelliJ-Token: $TOKEN" \
  "http://127.0.0.1:17777/run-configs/MySpringApp/logs/console/tail?lines=50"
```

### Multi-Project Development

```bash
# List all projects
curl -H "X-IntelliJ-Token: $TOKEN" http://127.0.0.1:17777/projects

# Start backend
curl -X POST -H "X-IntelliJ-Token: $TOKEN" \
  "http://127.0.0.1:17777/run-configs/Backend/run?project=my-backend"

# Start frontend (different project)
curl -X POST -H "X-IntelliJ-Token: $TOKEN" \
  "http://127.0.0.1:17777/run-configs/DevServer/run?project=my-frontend"
```

### MCP Integration

```python
import requests

class IntelliJMCP:
    def __init__(self, token):
        self.base = "http://127.0.0.1:17777"
        self.headers = {"X-IntelliJ-Token": token}

    def list_configs(self, project=None):
        params = {"project": project} if project else {}
        return requests.get(f"{self.base}/run-configs",
                          headers=self.headers, params=params).json()

    def run(self, name, project=None):
        params = {"project": project} if project else {}
        return requests.post(f"{self.base}/run-configs/{name}/run",
                           headers=self.headers, params=params).json()

    def get_logs(self, name, source, offset=0, limit=100):
        return requests.get(
            f"{self.base}/run-configs/{name}/logs/{source}",
            headers=self.headers,
            params={"offset": offset, "limit": limit}
        ).json()

    def search_logs(self, name, source, query, max_results=50):
        return requests.get(
            f"{self.base}/run-configs/{name}/logs/{source}/search",
            headers=self.headers,
            params={"q": query, "maxResults": max_results}
        ).json()
```

---

## Response Examples

### List Run Configurations
```json
[
  {
    "name": "MySpringApp",
    "type": "Spring Boot",
    "actions": ["run", "debug", "stop", "restart"],
    "status": "running"
  }
]
```

### Log Content (Paginated)
```json
{
  "source": "server.log",
  "totalLines": 1543,
  "offset": 0,
  "limit": 100,
  "lines": ["line 1", "line 2", "..."],
  "hasMore": true
}
```

### Search Results
```json
{
  "query": "ERROR",
  "caseSensitive": false,
  "results": [
    {
      "source": "server.log",
      "line": 342,
      "content": "2025-11-20 14:23:15 ERROR Connection failed",
      "context": ["line before", "match", "line after"]
    }
  ]
}
```

---

## Configuration

**Settings → Tools → RunControl**

- **Enable HTTP API** - Toggle on/off
- **Port** - Default: `17777` (restart required if changed)
- **API Token** - Auto-generated (click "Regenerate Token" for new one)

---

## Security

🔒 **Localhost only** - Server binds to `127.0.0.1`
🔒 **Token authentication** - Required on every request
🔒 **Auto-generated tokens** - Secure UUID-based tokens

---

## Building from Source

```bash
git clone https://github.com/SagenKoder/IntelliJ-RunControl.git
cd IntelliJ-RunControl
./gradlew buildPlugin
```

Output: `build/distributions/IntelliJ-RunControl-1.0.2.zip`

---

## Technical Details

- **Language**: Kotlin 2.1.0
- **Platform**: IntelliJ IDEA 2024.1+ (builds 241-253.*)
- **Server**: Jetty 11
- **APIs**: Native IntelliJ Platform SDK

---

## Status Values

| Status | Description |
|--------|-------------|
| `idle` | Not running |
| `running` | Process active |
| `debugging` | Debug mode active |
| `stopping` | Process terminating |
| `finished` | Process terminated |

---

## Troubleshooting

### Port Already in Use
Change port in **Settings → Tools → RunControl** and restart IntelliJ.

### 401 Unauthorized
Verify token matches **Settings → Tools → RunControl**.

### Server Not Starting
Check **Help → Show Log** and search for "RunControl".

---

## Contributing

Contributions welcome! Please submit issues and pull requests.

---

## License

MIT License - see [LICENSE](LICENSE) for details.

---

## Links

- **Source**: https://github.com/SagenKoder/IntelliJ-RunControl
- **Issues**: https://github.com/SagenKoder/IntelliJ-RunControl/issues
- **IntelliJ Platform SDK**: https://plugins.jetbrains.com/docs/intellij/

---

Built with ❤️ by [SagenKoder](https://github.com/SagenKoder)
