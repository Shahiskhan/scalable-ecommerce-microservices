# Commerce — modern storefront + distributed backend
A responsive commerce frontend lives in `frontend/`; the independently buildable Spring Boot platform is isolated in `backend/`.

## Frontend
```powershell
Set-Location frontend
npm install
npm run dev
```

The backend environment helper creates `frontend/.env.local`; set `VITE_FIREBASE_API_KEY`, `VITE_FIREBASE_AUTH_DOMAIN`, `VITE_FIREBASE_PROJECT_ID`, and `VITE_API_BASE_URL` there. Firebase sign-in is intentionally required: the API gateway verifies Firebase ID tokens and does not provide a local auth bypass. The app provides a polished catalog experience, search/filter controls, basket persistence, Firebase sign-in, and authenticated checkout/order status. Product browsing works in demo preview without API configuration; protected operations require backend + Firebase configuration.

## Backend
See [`backend/README.md`](backend/README.md) for local Docker Compose startup and independent service verification. Compose files, scripts, source services, config, contracts, and operational docs are all contained in `backend/`.

This repository is an implementation foundation, not a claim of production-grade availability or hyper-scale. Review [`backend/docs/operations.md`](backend/docs/operations.md) before any production deployment: provider-backed payments, HA infrastructure, backups/restores, TLS/mTLS, integration/failure/load testing, and monitoring/incident runbooks remain required.
