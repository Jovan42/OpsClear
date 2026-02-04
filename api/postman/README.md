# OpsClear Postman Collection

API collection for manual testing and documentation.

## Setup

1. Install [Postman](https://www.postman.com/downloads/)
2. Import collection: `OpsClear.postman_collection.json`
3. Import environment: `environments/local.postman_environment.json`
4. Select "OpsClear - Local" environment in Postman

## Usage

### Authentication Flow

1. Run **Auth > Register** to create a user (or use existing)
2. Run **Auth > Login** - token is auto-saved to `accessToken` variable
3. All other requests automatically use the token

### Environments

| Environment | Use for |
|-------------|---------|
| `local` | Local development (localhost:8080) |
| `staging` | Staging server (add when available) |

## Structure

```
postman/
├── OpsClear.postman_collection.json    # Main collection
├── environments/
│   └── local.postman_environment.json  # Local dev environment
└── README.md                           # This file
```

## Notes

- This collection is for **manual testing** and **documentation**
- Automated tests are in `backend/src/test/` (JUnit + Testcontainers)
- Collection is updated as new endpoints are added
