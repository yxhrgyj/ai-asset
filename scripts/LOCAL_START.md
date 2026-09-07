# Local Startup

Double-click `start-local.cmd` in the project root. It opens the portal and administration site after checking both services.

- Portal: http://127.0.0.1:5173/
- Administration: http://127.0.0.1:5173/admin
- Requirements: Node.js 22+, Java 21, Maven, and the existing PostgreSQL database on port 5432.
- Existing healthy services are reused. Stop the backend before launching again when Java source changes require rebuilding.
- Logs: `target/local-backend.log` and `target/local-frontend.log`.
- Services continue running after the launcher exits. Startup failure does not terminate existing services.
- `start-local.cmd --no-browser` starts/checks services without opening browser tabs.
- The current worktree reuses the original sibling project's attachment directory when present. Set `ASSET_STORAGE_ROOT` to override it.
- The script does not create, reset, or seed the database. It uses the application's existing database configuration and migrations.
