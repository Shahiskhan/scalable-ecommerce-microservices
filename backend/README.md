# Commerce backend
This directory contains the independent Spring Boot services, config/discovery infrastructure, Compose topology, contracts, scripts and operations documentation. Run commands from this directory.

## Local startup
```powershell
./scripts/new-local-env.ps1
# Edit backend/.env and set FIREBASE_PROJECT_ID
./scripts/verify.ps1
docker compose --env-file .env config --quiet
docker compose --env-file .env up --build --wait
```

Only the API gateway is published, at `http://localhost:8080`. The gateway requires a valid Firebase ID token; see the root README for frontend setup and use a Firebase client SDK to sign in. Local Payment uses a sandbox; no money moves. Docker Compose must be run from this directory so relative build and config paths resolve correctly.

To run the independent Maven builds: `./scripts/verify.ps1` (PowerShell) or `sh scripts/verify.sh` (Linux/macOS). For production limitations and rollout requirements, see `docs/operations.md`.
