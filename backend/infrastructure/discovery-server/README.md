# Discovery server

Independent Java 25 build: `mvn verify`. Port8761. `EUREKA_USERNAME` and `EUREKA_PASSWORD` are required. Basic authentication protects the registry and dashboard; health probes are anonymous. Registry API writes have CSRF disabled to support machine clients; the dashboard retains CSRF protections. The local singleton does not register with itself. Production needs multiple peer-aware instances across failure domains, TLS, restricted network access, and stable hostnames. Self-preservation stays enabled. Never expose this registry to the public internet.
