# L7 Guardian Proxy

A lightweight **Layer-7 HTTP guardian / reverse proxy** written in Java, focused on **explicit allowlisting, request validation, and safe forwarding**.

This project is developed **incrementally in phases**.  
**Current status: Phase 1 – Core Guarding & Forwarding (Complete)**

---

## Project Goals

L7 Guardian Proxy explores how an application-level proxy can act as a **policy enforcement point**, not a blind pass-through.

Core goals:
- Enforce **explicit request allowlists**
- Fail closed by default
- Apply defensive request validation
- Forward requests safely and transparently
- Remain simple, testable, and security-focused

### 🛠️ Tech Stack
- **Java 21** (LTS)
- **Spring Boot 3.4**
- **Virtual Threads** (Project Loom)
- **Maven**

This is a **learning-driven project**, intentionally built step-by-step to reflect real engineering trade-offs.

---

## ✅ Phase 1 Scope (Implemented)

Phase 1 focuses on **correctness, safety, and clean boundaries**.

### 🔐 Request Guarding
- Explicit **path allowlist** using a prefix-based trie
- Default-deny behavior (`403 Forbidden`)
- Allowlist evaluation based on request path only
- Query strings do **not** bypass allowlist rules

### 🔄 Request Mapping
- Incoming HTTP requests mapped into an immutable internal `ProxyRequest`
- Headers normalized and restricted headers filtered
- Request body fully buffered with a **configurable maximum size**
- Query strings correctly preserved and forwarded to the backend

### 🚫 Payload Protection
- Requests exceeding the configured body size are rejected
- Domain-specific exception: `RequestPayloadTooLargeException`
- Correct HTTP response returned: **413 Payload Too Large**

### 🌐 Safe Forwarding
- Forwarding logic abstracted behind a `ProxyForwarder` interface
- Backend failures isolated via `ProxyForwardingException`
- Backend status codes, headers, and response bodies are transparently returned

### 📤 Response Mapping
- Backend responses explicitly mapped back to HTTP responses
- No implicit passthrough of headers or body

### 🧪 Tests
- Unit tests covering:
    - Allowlist enforcement
    - Request body size limits
    - Request mapping behavior
- Tests assert **behavior**, not just implementation details

---

## Architecture Overview (Phase 1)

Request flow:

1. **GuardianFilter**  
   Validates the incoming request against the configured allowlist.  
   Requests that do not match are rejected with `403 Forbidden`.

2. **ProxyRequestMapper**  
   Maps the HTTP request into an internal immutable `ProxyRequest`.  
   Validates headers and enforces maximum payload size.

3. **ProxyForwarder**  
   Forwards the validated request to the configured backend.

4. **ProxyResponseMapper**  
   Maps the backend response back to an HTTP response.

5. **Client Response**  
   The response is returned transparently to the client.

---
### Design principles:
- Clear separation of responsibilities
- Explicit domain exceptions for policy violations
- No framework magic or hidden behavior

---

## ⚙️ Configuration

Example configuration:

```yaml
proxy:
  max-body-size: 16384 # 16 KB
  guardian:
    security:
      allowed-paths:
        - /api/v1
        - /test
        - /actuator/health
  ```

#### Notes:

- "/" is not allowed by default (intentional security decision)
- All validation is enforced before forwarding

#### 🚧 Explicitly Out of Scope (Phase 1)

The following are intentionally deferred:
- Streaming request bodies
- Authentication / authorization
- Rate limiting or quotas
- TLS termination
- Observability metrics
- Production deployment concerns

### 🛣️ Planned Next Phases (High-Level)
Phase 2 – Hardening & Control:
- Streaming bodies (no full buffering)
- Rate limiting and quotas
- Centralized error handling
- Metrics and observability hooks

Phase 3 – Security & Identity
- Authentication integration
- Identity propagation
- Policy-based routing

#### 🧠 Why This Project Exists

This repository exists to:
- Explore L7 proxy responsibilities
- Practice defensive API and security-first design
- Build clean, testable infrastructure code
- Reflect real-world engineering decision-making
- It prioritizes clarity and correctness over feature count.


## 🚀 How to Run

### Prerequisites
- Java 21 (required — used for virtual threads)
- Maven (or use the included Maven Wrapper)

### Run locally

```bash
# Clone the repository
git clone https://github.com/EithanAlexander/l7-guardian-proxy.git

# Enter the project directory
cd l7-guardian-proxy

# Run the application
./mvnw spring-boot:run
```

