# Keycloak Configuration

This folder contains Keycloak realm configuration for OpsClear.

## Files

| File | Purpose |
|------|---------|
| `realm-export.json` | Realm configuration (auto-imported on startup) |

## Realm: `opsclear`

### Clients

| Client ID | Type | Purpose |
|-----------|------|---------|
| `opsclear-frontend` | Public (PKCE) | React SPA |
| `opsclear-backend` | Bearer-only | Spring Boot API |

### Settings

| Setting | Value |
|---------|-------|
| User registration | Enabled |
| Email as username | Yes |
| Password policy | Min 8 characters |
| Access token lifespan | 5 minutes |
| Refresh token lifespan | 30 days |
| Brute force protection | Enabled |

### Test User

For development only:
- Email: `testuser@example.com`
- Password: `password123`

## Access Points

| URL | Purpose |
|-----|---------|
| http://localhost:8180 | Keycloak home |
| http://localhost:8180/admin | Admin console (admin/admin) |
| http://localhost:8180/realms/opsclear/account | User account management |

## Getting a Token (for testing)

```bash
# Login and get tokens
curl -X POST http://localhost:8180/realms/opsclear/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=opsclear-frontend" \
  -d "grant_type=password" \
  -d "username=testuser@example.com" \
  -d "password=password123"
```

## Updating Realm Config

1. Make changes in Keycloak admin console
2. Export realm:
   ```bash
   docker exec opsclear-keycloak /opt/keycloak/bin/kc.sh export \
     --dir /tmp --realm opsclear
   docker cp opsclear-keycloak:/tmp/opsclear-realm.json ./keycloak/realm-export.json
   ```
3. Commit the updated `realm-export.json`

## Production Notes

- Change admin password
- Enable SSL (`sslRequired: all`)
- Configure SMTP for email verification
- Remove test user
- Use proper secrets management
