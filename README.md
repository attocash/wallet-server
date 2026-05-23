# Atto Wallet Server

Atto Wallet Server is a self-hosted HTTP API for managing Atto wallets and accounts. It stores encrypted wallet
mnemonics, derives deterministic account addresses, signs wallet operations through `commons-wallet`, talks to an Atto
node and proof-of-work worker, and persists wallet/account state in MySQL.

[Website](https://atto.cash/) | [Docs](https://atto.cash/docs/integration) | [Commons](https://github.com/attocash/commons)

## Table of contents

- [What this service does](#what-this-service-does)
- [Current runtime](#current-runtime)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Ports and observability](#ports-and-observability)
- [API overview](#api-overview)
- [Common flows](#common-flows)
- [Operational behavior](#operational-behavior)
- [Code map](#code-map)
- [Testing](#testing)
- [Docker and native image](#docker-and-native-image)
- [Troubleshooting](#troubleshooting)

## What this service does

At a high level, this service:

1. Creates or imports named wallets from 24-word Atto mnemonics.
2. Encrypts mnemonic entropy at rest and keeps wallet signing material available only while a wallet is unlocked.
3. Derives wallet account addresses by deterministic index.
4. Opens accounts, receives funds, sends funds, and changes representatives using `commons-wallet` `AttoWallet`.
5. Tracks enabled accounts through an Atto node account monitor and auto-receives available receivables.
6. Streams account entries from the node for known wallet accounts.
7. Publishes optional callbacks for new account entries.
8. Persists wallet rows, account rows, and callback progress in MySQL through Flyway + R2DBC.

The server is intentionally not a node. It delegates ledger state, transaction publication, account-entry streaming, and
receivable discovery to the configured Atto node endpoint.

## Current runtime

- Kotlin: 2.3.21
- Java toolchain: 25
- Spring Boot: 4.0.3
- Atto Commons: 6.6.0
- HTTP stack: Spring WebFlux
- Database: MySQL with Flyway migrations and R2DBC runtime access
- API documentation: Springdoc OpenAPI + Swagger UI
- Native image: GraalVM static binary, packaged in a scratch container

Spring Boot 4 splits several auto-configuration areas into separate starters. This application currently depends on:

- `spring-boot-starter-webflux` for the HTTP server
- `spring-boot-starter-webclient` for node/work/callback clients
- `spring-boot-starter-flyway` for Flyway auto-configuration
- `spring-boot-starter-data-r2dbc` for runtime database access
- `spring-boot-starter-actuator` for health, metrics, and Prometheus

## Quick start

### Prerequisites

- Java 25, or a JDK that can resolve the Gradle Java 25 toolchain
- MySQL 8.x
- An Atto node HTTP endpoint
- An Atto work HTTP endpoint
- Docker or Podman for Testcontainers

### 1. Start MySQL for local development

```bash
podman run --rm --name atto-wallet-server-mysql \
  -e MYSQL_ALLOW_EMPTY_PASSWORD=yes \
  -e MYSQL_DATABASE=atto \
  -p 3306:3306 \
  mysql:8.2
```

Docker can be used instead of Podman with the same arguments.

### 2. Export the required environment

```bash
export NETWORK=LOCAL
export NODE_BASE_URL=http://localhost:8080
export WORK_BASE_URL=http://localhost:8085
export CHA_CHA20_KEY_ENCRYPTION_KEY=0000000000000000000000000000000000000000000000000000000000000000

export ATTO_DB_HOST=localhost
export ATTO_DB_PORT=3306
export ATTO_DB_NAME=atto
export ATTO_DB_USER=root
export ATTO_DB_PASSWORD=
```

`NODE_BASE_URL` must point to the Atto node HTTP API, not the node-to-node WebSocket port. `WORK_BASE_URL` must point to
the Atto work HTTP API. The test suite starts Commons mocks for both services automatically.

### 3. Run the server

```bash
./gradlew bootRun --no-daemon
```

If your local node already uses port `8080`, run the wallet server on a different port:

```bash
./gradlew bootRun --no-daemon \
  --args='--server.port=8090 --management.server.port=8091'
```

Open Swagger UI at:

```text
http://localhost:8080/
```

When using the alternate ports above, Swagger UI is at `http://localhost:8090/`.

## Configuration

Configuration is defined in `src/main/resources/application.yaml`.

### Required environment variables

| Variable | Purpose |
| --- | --- |
| `NETWORK` | Atto network name used by Commons, for example `LOCAL`. |
| `NODE_BASE_URL` | Base URL of the Atto node HTTP API. |
| `WORK_BASE_URL` | Base URL of the Atto proof-of-work service. |
| `CHA_CHA20_KEY_ENCRYPTION_KEY` | 32-byte hex key used to encrypt each wallet encryption key at rest. |
| `ATTO_DB_HOST` | MySQL host. Defaults to `localhost`. |
| `ATTO_DB_PORT` | MySQL port. Defaults to `3306`. |
| `ATTO_DB_NAME` | MySQL database. Defaults to `atto`. |
| `ATTO_DB_USER` | MySQL user. Defaults to `root`. |
| `ATTO_DB_PASSWORD` | MySQL password. Defaults to empty. |

### Optional callback configuration

| Variable | Purpose |
| --- | --- |
| `CALLBACK_URL` | URL that receives account-entry callbacks. Defaults to an internal no-op endpoint. |
| `CALLBACK_HEADER_KEY` | Optional header name added to callback requests. |
| `CALLBACK_HEADER_VALUE` | Optional header value added to callback requests. |

Callbacks publish `AccountEntry` payloads. Callback progress is persisted, so the notifier can resume from the last
successfully published height.

### Database access

The application uses two database connections:

- R2DBC URL for runtime repositories:
  `r2dbc:mysql://${ATTO_DB_HOST}:${ATTO_DB_PORT}/${ATTO_DB_NAME}`
- JDBC URL for Flyway migrations:
  `jdbc:mysql://${ATTO_DB_HOST}:${ATTO_DB_PORT}/${ATTO_DB_NAME}`

Flyway migrations live in `src/main/resources/db/migration`.

## Ports and observability

| Port | Purpose |
| --- | --- |
| `8080` | Public wallet API and Swagger UI. |
| `8081` | Actuator management server. |

Management endpoints are exposed at the management root path:

- `http://localhost:8081/health`
- `http://localhost:8081/metrics`
- `http://localhost:8081/prometheus`

Metrics include the `application=atto-wallet-server` tag.

## API overview

Swagger UI is available at `/` on the public API port. The main public endpoints are listed below.

### Wallets

| Method | Path | Description |
| --- | --- | --- |
| `GET` | `/wallets` | List stored wallets and lock state. |
| `GET` | `/wallets/{name}` | Get wallet metadata. |
| `POST` | `/wallets/{name}` | Create a new wallet with a generated mnemonic and encryption key. |
| `PUT` | `/wallets/{name}` | Import a wallet from a 24-word mnemonic. |
| `POST` | `/wallets/{name}/recoveries` | Recover deterministic account rows for an imported wallet using gap-based discovery. |
| `PUT` | `/wallets/{name}/locks/LOCKED` | Lock a wallet by removing its stored encrypted encryption key. |
| `PUT` | `/wallets/{name}/locks/UNLOCKED` | Unlock a wallet with its encryption key. |

### Accounts

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/wallets/{walletName}/accounts` | Create the next deterministic account for a wallet. |
| `POST` | `/wallets/{walletName}/accounts/ranges/{toIndex}` | Create deterministic accounts through an index. |
| `GET` | `/wallets/{walletName}/accounts` | List persisted accounts for a wallet. |
| `GET` | `/wallets/accounts/{address}` | Get a persisted account row by address. |
| `GET` | `/wallets/accounts/{address}/details` | Get live account state known by the wallet runtime. |
| `POST` | `/wallets/accounts/{address}/states/DISABLED` | Disable an account. |
| `POST` | `/wallets/accounts/{address}/states/ENABLED` | Enable an account. |
| `POST` | `/wallets/accounts/{address}/transactions/SEND` | Send funds from an opened account. |
| `POST` | `/wallets/accounts/{address}/transactions/CHANGE` | Change account representative. |
| `POST` | `/wallets/accounts/entries` | Stream account entries for known accounts. |

## Common flows

Set a base URL for the examples:

```bash
BASE=http://localhost:8080
```

### Create a wallet

```bash
curl -sS -X POST "$BASE/wallets/treasury"
```

The response contains the generated mnemonic and encryption key. Store both securely. Losing either one can make the
wallet unrecoverable through this server.

### Import an existing wallet

```bash
curl -sS -X PUT "$BASE/wallets/treasury" \
  -H 'content-type: application/json' \
  -d '{
    "mnemonic": "word1 word2 word3 ... word24",
    "encryptionKey": "0000000000000000000000000000000000000000000000000000000000000000"
  }'
```

`encryptionKey` is optional on import. If omitted, the server generates one and returns it.

### Recover wallet accounts

Recover an already imported and unlocked wallet from its highest persisted account index:

```bash
curl -sS -X POST "$BASE/wallets/treasury/recoveries" \
  -H 'content-type: application/json' \
  -d '{
    "gapLimit": 20
  }'
```

The server derives each address, asks the node for current account state, creates missing account rows, and refreshes the
wallet runtime so auto-receive can pick up receivables for recovered addresses. Recovery scans until `gapLimit`
consecutive unopened accounts are found, then creates accounts through the latest opened index it discovered. If no
opened account is discovered, it creates the initial scanned gap window.

### Lock and unlock a wallet

```bash
curl -sS -X PUT "$BASE/wallets/treasury/locks/LOCKED"

curl -sS -X PUT "$BASE/wallets/treasury/locks/UNLOCKED" \
  -H 'content-type: application/json' \
  -d '{
    "encryptionKey": "0000000000000000000000000000000000000000000000000000000000000000"
  }'
```

### Create an account

```bash
curl -sS -X POST "$BASE/wallets/treasury/accounts"
```

Create all deterministic accounts through a fixed index:

```bash
curl -sS -X POST "$BASE/wallets/treasury/accounts/ranges/10"
```

The response includes:

- `address`: bare address path used by the API
- `displayAddress`: display form, usually prefixed with `atto://`
- `index`: deterministic wallet index

### Send funds

```bash
ADDRESS=aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2
RECEIVER=aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2

curl -sS -X POST "$BASE/wallets/accounts/$ADDRESS/transactions/SEND" \
  -H 'content-type: application/json' \
  -d "{
    \"receiverAddress\": \"$RECEIVER\",
    \"amount\": 10000,
    \"lastHeight\": 1
  }"
```

`lastHeight` is optional, but clients should send it when they have the current height. It protects clients from stale
or duplicate send attempts. If the supplied height does not match the server's latest known account height, the server
returns `409 Conflict`.

### Change representative

```bash
ADDRESS=aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2
REPRESENTATIVE=aa36n56jj5scb5ssb42knrtl7bgp5aru2v6pd2jspj5axdw2iukun6r2du4k2

curl -sS -X POST "$BASE/wallets/accounts/$ADDRESS/transactions/CHANGE" \
  -H 'content-type: application/json' \
  -d "{
    \"representativeAddress\": \"$REPRESENTATIVE\"
  }"
```

### Fetch account entries

Request entries for a specific account and starting height:

```bash
curl -sS -X POST "$BASE/wallets/accounts/entries" \
  -H 'content-type: application/json' \
  -d "{
    \"search\": [
      {
        \"address\": \"$ADDRESS\",
        \"fromHeight\": 1
      }
    ]
  }"
```

Send no body to fetch entries for all enabled accounts that are currently open in the wallet runtime:

```bash
curl -sS -X POST "$BASE/wallets/accounts/entries"
```

## Operational behavior

### Wallet locking

Wallet mnemonic entropy is always stored encrypted. When a wallet is unlocked, the wallet-specific encryption key is
also stored encrypted with `CHA_CHA20_KEY_ENCRYPTION_KEY`, allowing the runtime to recreate the mnemonic after restart.

Locking a wallet clears that encrypted wallet key. The persisted wallet remains, but signing operations are unavailable
until the wallet is unlocked again with the wallet encryption key.

### Account derivation and address discovery

Accounts are deterministic by wallet index. `POST /wallets/{walletName}/accounts` creates the next index based on the
highest account index already persisted for that wallet.

Importing a mnemonic does not automatically scan every possible derivation index. If you re-import a mnemonic into an
empty database and need to rediscover opened addresses, call `POST /wallets/{walletName}/recoveries` with a `gapLimit`
to scan from the wallet's highest persisted index until consecutive unopened accounts are found. Recovery creates local
account rows through the latest opened index it discovers, or the initial scanned gap window when no opened account is
found. If you already know the account range to open locally, call
`POST /wallets/{walletName}/accounts/ranges/{toIndex}`. Existing persisted accounts are reopened automatically when
their wallet is unlocked.

### Opened vs persisted accounts

An account row can exist before the account exists on-chain. `GET /wallets/accounts/{address}` returns the persisted
row, while `GET /wallets/accounts/{address}/details` returns live account details only after the account has opened.

Unopened accounts return `404` from details, send, and change flows with the account-not-open message. This behavior is
preserved even when the wallet is locked.

### Auto-receive

The service monitors enabled accounts in unlocked wallets using Commons node monitors. When a receivable appears, it
delegates receive/open block creation and publication to `AttoWallet`.

Disabled accounts are removed from the receive monitor until they are enabled again.

### Address wire format

Public API address fields use bare Atto address paths for compatibility with the 1.5 release. The API should not return
`atto://...` values in JSON address fields except for display-only fields such as `displayAddress` and framework error
messages.

## Code map

Common entry points:

- `src/main/kotlin/cash/atto/Application.kt` - Spring Boot entry point
- `src/main/kotlin/cash/atto/ApplicationConfiguration.kt` - scheduling, OpenAPI, runtime hints
- `src/main/resources/application.yaml` - runtime configuration
- `src/main/resources/db/migration` - Flyway migrations

Main packages:

- `wallet/` - wallet create/import/lock/unlock and encrypted mnemonic persistence
- `account/` - account persistence, deterministic account creation, send/change, receive monitoring, account entries
- `node/` - Atto node client configuration
- `work/` - Atto proof-of-work client configuration
- `notification/` - optional account-entry callback publisher

## Testing

Use a writable Gradle home outside the repository:

```bash
./gradlew test --no-daemon --fail-fast
```

The Cucumber tests start MySQL with Testcontainers and use Commons mock services:

- `AttoNodeMock` for a real mocked node
- `AttoWorkerMock` for proof-of-work

Account receive scenarios publish real funding transactions to the mocked node instead of pushing entries into an
in-memory fake.

Useful broader checks:

```bash
./gradlew build --no-daemon
```

## Docker and native image

The container image expects a native GraalVM binary at `build/native/nativeCompile/wallet-server`.

Build the native binary:

```bash
./gradlew nativeCompile --no-daemoqn
```

Build the container:

```bash
podman build \
  --build-arg APPLICATION_VERSION=local \
  -t atto-wallet-server:local \
  .
```

Run the container with the same environment variables described above:

```bash
podman run --rm --network host \
  -e NETWORK \
  -e NODE_BASE_URL \
  -e WORK_BASE_URL \
  -e CHA_CHA20_KEY_ENCRYPTION_KEY \
  -e ATTO_DB_HOST \
  -e ATTO_DB_PORT \
  -e ATTO_DB_NAME \
  -e ATTO_DB_USER \
  -e ATTO_DB_PASSWORD \
  atto-wallet-server:local
```

The image is built from `scratch`, runs as user `65532:65532`, and exposes ports `8080` and `8081`.

CI builds and publishes images to GitHub Container Registry with tags for the commit SHA and branch name.

## Troubleshooting

### The app fails on startup with missing configuration

`NETWORK`, `NODE_BASE_URL`, `WORK_BASE_URL`, and `CHA_CHA20_KEY_ENCRYPTION_KEY` are required by `application.yaml`.
Set them explicitly even for local development.

### Wallet operations fail with "Wallet <name> is locked"

The wallet exists, but its encryption key is not available to the runtime. Unlock it with:

```bash
curl -sS -X PUT "$BASE/wallets/<name>/locks/UNLOCKED" \
  -H 'content-type: application/json' \
  -d '{"encryptionKey":"<64-hex-key>"}'
```

### Send or change fails because the account is not open

The address has been persisted by the wallet server, but the account does not yet exist on-chain. Receive funds first,
then retry the operation after account details become available.

### Sends fail with `409 Conflict`

The supplied `lastHeight` is stale. Fetch the latest account details or account entries, then retry with the current
height.

### Receives do not happen automatically

Check that:

- the wallet is unlocked
- the account is enabled
- `NODE_BASE_URL` points to a reachable node
- the node can stream receivables for the account
- `WORK_BASE_URL` points to a reachable proof-of-work service

### Callbacks are not delivered

If `CALLBACK_URL` is empty, callbacks go to the internal no-op endpoint. Set `CALLBACK_URL` and optional callback header
environment variables, then check application logs for callback failures.

### Tests fail before the application starts

The tests require a working Docker or Podman environment for Testcontainers. In this local environment, use
`GRADLE_USER_HOME=/tmp/gradle-home` to avoid Gradle cache permission issues.

## License

See [LICENSE](./LICENSE).
