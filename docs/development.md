# Development and deployment

## Local development

Use Java 21 and the Clojure CLI. Create `data/`, then start a REPL:

| Environment | Command |
| --- | --- |
| Terminal | `clj -M:dev` |
| nREPL | `clj -M:dev:nrepl` or `make repl` |
| CIDER | `clj -M:dev:cider` |
| Cursive | Project-classpath REPL with the `dev` alias |

See the [Cursive setup](https://cursive-ide.com/userguide/repl.html) and
[CIDER setup](https://docs.cider.mx/cider/basics/up_and_running.html) for editor integration.
The nREPL aliases are development-only; production nREPL requires Kit's
[kit-nrepl profile](https://kit-clj.github.io/docs/profiles.html#profiles).

```clojure
(go)    ; Start the application, run migrations, and start the tick scheduler.
(reset) ; Reload changed namespaces and restart the system.
(halt)  ; Stop the system.
```

The application defaults to [localhost:3333](http://localhost:3333).
[/health](http://localhost:3333/health) reports server and database readiness.
Configuration lives in [`resources/system.edn`](../resources/system.edn):

- `PORT`: HTTP port, default `3333`.
- `HTTP_HOST`: bind address, default `0.0.0.0`.
- `DATABASE_URL`: JDBC URL, default `jdbc:sqlite:data/pommes-connection.db`.

Run tests with `clj -M:test` or `make test`.
Build the standalone JAR with `clj -T:build all` or `make uberjar`.

## Game data and balancing

[`game_data.clj`](../src/clj/dev/lhrb/pommes/pommes_connection/game_data.clj)
contains the `game-data` map: catalog, costs, demographics, rules, setup limit, and starter setup.
After editing, run `(reset)`; existing games use updated values on their next page load and tick.

Pure market-analysis helpers need neither the web application nor a database:

```clojure
(require '[dev.lhrb.pommes.pommes-connection.market-analysis :as market-analysis])

;; Per-player and minimum/maximum/average profit across all named scenarios.
(market-analysis/scenario-report)

;; Explore allocations and utility components for one scenario.
(market-analysis/run-scenario :price-pressure 10)
```

Scenarios live in `market-analysis/scenarios`.
Reports compare tick 1's immediate result with tick 10, after market memory adapts.
See [`balancing.md`](../balancing.md) for the balancing specification.

## Deployment

The deployment configuration targets one Ubuntu 24.04 server with Docker Compose and Caddy.
GitHub Actions runs tests, builds each `main` commit's image, transfers it over SSH, and invokes `/opt/pommes/deploy.sh`.
The deploy script checks container health and restores the previous image if activation fails.

**The bootstrap script is incomplete.** In [`infra/setup-server.sh`](../infra/setup-server.sh),
`configure_app` and `install_backup_timer` are disabled, and `app_user` is undefined.
Do not treat `sudo ./infra/setup-server.sh` as a complete unattended setup.
It currently installs Docker and configures UFW for SSH on port 22, HTTP, and HTTPS.

Complete or manually perform the application and backup setup before deploying:

- Create `/opt/pommes/{data,releases,backups}` and install the files referenced by the setup script.
- Copy [`infra/.env.example`](../infra/.env.example) to `/opt/pommes/.env`.
- Set `DOMAIN`, `ACME_EMAIL`, and a persistent `SESSION_KEY` from `openssl rand -base64 16`.
- Point the domain's A/AAAA records at the server.
- Match permissions and Docker access to the deployment SSH user; log in again after group changes.
- Install the supplied systemd service and timer for daily local SQLite backups under `/opt/pommes/backups`.

The current workflow reads two secrets: `DEPLOY_HOST` and `DEPLOY_SSH_KEY`.
Set `DEPLOY_HOST` to the SSH target, such as `user@host`.
`DEPLOY_USER` and `DEPLOY_PORT` appeared in earlier instructions but are not read by the current workflow.
Nondefault SSH ports require workflow changes.
