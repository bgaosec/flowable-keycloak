# Flowable REST with Keycloak JWT

Custom Spring Security module plus Docker image to protect Flowable REST endpoints with Keycloak-issued JWTs.

## Project Layout
- `flowable-rest-keycloak-security/pom.xml`: Maven module that builds the security configuration JAR.
- `flowable-rest-keycloak-security/src/main/java/com/example/flowable/security/SecurityConfig.java`: Secures Flowable REST using Spring Security OAuth2 Resource Server with Keycloak roles.
- `Dockerfile.flowable-rest-keycloak`: Builds a custom image extending `flowable/flowable-rest:latest` and adds the JAR to `/app/WEB-INF/lib`.
- `docker-compose.yml`: Runs Postgres and the custom Flowable REST image with Keycloak/JWT configuration.

## Build
```bash
cd flowable-rest-keycloak-security
mvn clean package
```

Outputs `target/flowable-rest-keycloak-security.jar` (non-executable; intended to be loaded by the Flowable REST app).

## Build Docker Image
```bash
cd ..
docker build -f Dockerfile.flowable-rest-keycloak -t 192.168.1.129:5000/flowable-rest-kc:latest .
docker push   192.168.1.129:5000/flowable-rest-kc:latest
```

The base image loads any additional jars under `/app/libs`, so the COPY step places the security module on the classpath.

## Run
```bash
docker-compose up -d
```

Environment highlights (see `docker-compose.yml`):
- `FLOWABLE_COMMON_APP_SECURITY_ENABLED=true`
- `FLOWABLE_REST_APP_ADMIN_USER_ID=false` (skips default admin bootstrap that requires IDM)
- `FLOWABLE_REST_APP_CREATE_DEMO_DEFINITIONS=false`
- `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=https://keycloak.example.com/realms/flowable`
- Optional: `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`

## Test
After obtaining a Keycloak access token:
```bash
curl -H "Authorization: Bearer <ACCESS_TOKEN>" \
     http://localhost:8000/service/runtime/process-instances
```

Expected: `401` for missing/invalid token, `200` for valid token with sufficient roles, `403` for valid token lacking roles (if you later add role-based checks).

## Troubleshooting
- Verify issuer/JWK URIs match the Keycloak realm.
- Ensure tokens include a flat `roles` claim (mapped to `ROLE_*` authorities).
- Check container logs with `docker-compose logs -f flowable-rest`.
