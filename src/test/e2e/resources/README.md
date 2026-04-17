# E2E Tests

These tests call a running SafeTransfer application over HTTP.

Default base URL:

- `http://localhost:8080`

Override with:

- `SAFETRANSFER_BASE_URL`

Run:

```powershell
.\gradlew.bat e2eTest
```

Example:

```powershell
$env:SAFETRANSFER_BASE_URL = "http://localhost:8080"
.\gradlew.bat e2eTest
```
