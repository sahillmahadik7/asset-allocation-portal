# Week 14 - Health Checks and Rollback

## Health Check
Run:
powershell -ExecutionPolicy Bypass -File .\scripts\week14-health-check.ps1

The script verifies that:
- The asset-portal Docker container is running.
- The application responds with HTTP 200 on port 8081.

Exit code 0 indicates success. A nonzero exit code indicates failure.

## Rollback Strategy
Current deployment image: asset-allocation-portal:41
Previous image candidate: asset-allocation-portal:v1
Container: asset-portal
Application port: 8081

Before rollback:
1. Confirm the previous image exists.
2. Verify its configuration and database connectivity.
3. Confirm the application starts and passes the health check.
4. Preserve the current image and database.
5. Record the reason for rollback.

Do not replace the live container until the rollback image has been validated.
Do not remove or reset the PostgreSQL container or database.

After a rollback, rerun the health-check script and record the result.
