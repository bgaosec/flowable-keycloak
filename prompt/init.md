# Development Prompt for Custom Flowable REST + Keycloak Integration (Option 2)

You are a senior Java/Spring Boot engineer experienced with Flowable, OAuth2, and Keycloak.

I have a **Docker-based Flowable REST deployment** and I want to implement **Option 2**: build a **custom Java project that produces a JAR with Spring Security configuration for Keycloak JWT**, build a **custom Docker image** based on `flowable/flowable-rest:latest`, and provide **deployment instructions**.

---

## 1. Current Setup

Here is my current `docker-compose.yml`:

```yaml
services:
  db:
    image: postgres:14
    environment:
      POSTGRES_DB: flowable
      POSTGRES_USER: flowable
      POSTGRES_PASSWORD: flowable
    volumes:
      - ./data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U flowable -d flowable"]
      interval: 5s
      timeout: 5s
      retries: 20

  flowable-rest:
    image: flowable/flowable-rest:latest
    depends_on:
      db:
        condition: service_healthy
    environment:
      spring.datasource.driver-class-name: org.postgresql.Driver
      spring.datasource.url: jdbc:postgresql://db:5432/flowable
      spring.datasource.username: flowable
      spring.datasource.password: flowable
      flowable.database-schema-update: "true"
      # Optional: adjust context path if you want REST under a different base path
      # server.servlet.context-path: /flowable-rest
    ports:
      - "8000:8080"
    restart: unless-stopped
```

I want to **replace** the `flowable-rest` service with a custom image that:

- Validates **Keycloak JWT access tokens** for the Flowable REST APIs.
- Uses **Spring Security OAuth2 Resource Server**.
- Disables Flowable IDM (users are managed only in Keycloak).
- Enforces that all `/service/**`, `/bpmn-api/**`, `/cmmn-api/**`, etc. endpoints require a valid token.
- Maps roles from Keycloak into Spring Security `ROLE_*` authorities.

---

## 2. Assumptions / Requirements

Assume:

- Keycloak realm: `flowable`
- Issuer URI: `https://keycloak.example.com/realms/flowable`
- I will configure a Keycloak client whose tokens include a **flat `roles` claim**, e.g.:

```json
{
  "sub": "user-123",
  "preferred_username": "john.doe",
  "roles": ["flowable-admin", "flowable-user"]
}
```

- Those `roles` must be mapped by Spring Security to authorities `ROLE_flowable-admin`, `ROLE_flowable-user`.
- Flowable must treat the **JWT subject / username** as the current authenticated user.

---

## 3. Deliverables

Produce **ALL of the following**:

### 3.1. Java Project Structure

Create a **minimal Maven-based Java project** called `flowable-rest-keycloak-security` whose output is a JAR that plugs into Flowable REST.

Provide:

1. `pom.xml` with:
   - GroupId: `com.example`
   - ArtifactId: `flowable-rest-keycloak-security`
   - Packaging: `jar`
   - Dependencies:
     - `spring-boot-starter-security`
     - `spring-boot-starter-oauth2-resource-server`
     - Any necessary Flowable/Spring Boot dependencies to integrate with the existing Flowable REST app.
   - Build configuration so the resulting JAR is **runtime-only** config, not its own Spring Boot application.

2. Source layout:
   - `src/main/java/com/example/flowable/security/SecurityConfig.java`
   - Other classes if needed (e.g. a custom `JwtAuthenticationConverter`).

---

### 3.2. Spring Security Configuration

In `SecurityConfig.java`, configure:

- A `SecurityFilterChain` bean that:
  - Disables CSRF.
  - Protects Flowable REST endpoints:
    - `/service/**`, `/bpmn-api/**`, `/cmmn-api/**`, `/dmn-api/**`, etc. must be **authenticated**.
    - `/actuator/**` may be left open for health checks.
  - Uses `oauth2ResourceServer().jwt()`.

- A `JwtAuthenticationConverter` bean that:
  - Reads roles from the **`roles` claim**.
  - Prefixes them with `"ROLE_"`.

Example behavior:

```java
private JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtGrantedAuthoritiesConverter gac = new JwtGrantedAuthoritiesConverter();
    gac.setAuthoritiesClaimName("roles");
    gac.setAuthorityPrefix("ROLE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(gac);
    return converter;
}
```

- Ensure Spring Security uses the JWT’s subject / preferred username as the principal so Flowable sees `SecurityContextHolder.getContext().getAuthentication().getName()` as the Keycloak username.

---

### 3.3. External Configuration via Environment Variables

The Flowable REST app must be configurable **purely via environment variables**.

Document and show usage of:

- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI`
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`

Also set these Flowable properties:

- `FLOWABLE_IDM_ENABLED=false`
- `FLOWABLE_COMMON_APP_SECURITY_ENABLED=true`

---

### 3.4. Dockerfile for Custom Flowable REST Image

Create a Dockerfile named `Dockerfile.flowable-rest-keycloak`:

```dockerfile
FROM flowable/flowable-rest:latest

COPY target/flowable-rest-keycloak-security.jar /app/libs/flowable-rest-keycloak-security.jar
```

Explain the correct path for the library directory in the base image.

---

### 3.5. Updated docker-compose.yml

Produce an updated compose file that replaces the Flowable REST service with:

```yaml
flowable-rest:
  image: myorg/flowable-rest-keycloak:latest
  depends_on:
    db:
      condition: service_healthy
  environment:
    spring.datasource.driver-class-name: org.postgresql.Driver
    spring.datasource.url: jdbc:postgresql://db:5432/flowable
    spring.datasource.username: flowable
    spring.datasource.password: flowable
    flowable.database-schema-update: "true"

    FLOWABLE_IDM_ENABLED: "false"
    FLOWABLE_COMMON_APP_SECURITY_ENABLED: "true"

    SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI: "https://keycloak.example.com/realms/flowable"
    # Optional alternative
    # SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI: "https://keycloak.example.com/realms/flowable/protocol/openid-connect/certs"

  ports:
    - "8000:8080"
  restart: unless-stopped
```

---

### 3.6. Build & Deployment Instructions

Provide instructions for:

#### 1. Building the JAR

```bash
mvn clean package
```

#### 2. Building the Docker Image

```bash
docker build -f Dockerfile.flowable-rest-keycloak -t myorg/flowable-rest-keycloak:latest .
```

#### 3. Running the Stack

```bash
docker-compose up -d
```

#### 4. Testing

- Request a Keycloak access token.
- Call Flowable REST:

```bash
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \
     http://localhost:8000/service/runtime/process-instances
```

Expected:
- `401` → Missing or invalid token
- `200` → Valid token, successful request
- `403` → Token valid but insufficient roles (future enhancement)

#### 5. Troubleshooting

Document common issues:
- Incorrect issuer URI
- Missing `roles` claim
- JWK discovery errors
- Where to inspect container logs

---

## 4. Output Format

Please output **all components** clearly:

1. Complete `pom.xml`
2. Full `SecurityConfig.java` (and helper classes if necessary)
3. Full Dockerfile
4. Full updated `docker-compose.yml`
5. Markdown build & run instructions

Everything must be consistent and ready to copy into files and execute.

