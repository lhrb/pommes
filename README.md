# pommes-connection

Start a [REPL](#repls) in your editor or terminal of choice.

Start the server with:

```clojure
(go)
```

The health page is available at http://localhost:3000/health. It reports server
and database readiness.

By default, SQLite stores its database at `data/pommes-connection.db`. Set
`DATABASE_URL` to use another JDBC URL.

## Production deployment

Production runs on a single Ubuntu 24.04 server using Docker Compose and
Caddy. Run the bootstrap once from a checked-out repository as the SSH user
that GitHub Actions will use:

```sh
sudo ./infra/setup-server.sh
```

Log in again, edit `/opt/pommes/.env`, and set `DOMAIN` and `ACME_EMAIL`. Point
the domain's A/AAAA records at the server before the first deploy. The script
generates and stores the session key, configures UFW for SSH/HTTP/HTTPS, and
enables daily local SQLite backups under `/opt/pommes/backups`.

The deploy workflow runs tests, builds an image for each `main` commit, copies
it to the server, and activates it with `/opt/pommes/deploy.sh`. Configure
these GitHub Actions secrets: `DEPLOY_HOST`, `DEPLOY_PORT`, `DEPLOY_USER`, and
`DEPLOY_SSH_KEY`. `DEPLOY_USER` must be the user that ran the bootstrap script.

System configuration is available under `resources/system.edn`.

## Game data

All editable game parameters live in
`src/clj/dev/lhrb/pommes/pommes_connection/game_data.clj`. Its `game-data` map
contains the catalog, costs, demographics, rules, setup limit, and starter
setup. Reload the app after editing it; existing games use the updated values
on their next page load and tick.

To reload changes:

```clojure
(reset)
```

## Market balancing in the REPL

The pure market analysis helpers provide repeatable scenarios without starting
the web app or touching the database.

```clojure
(require '[dev.lhrb.pommes.pommes-connection.market-analysis :as market-analysis])

;; Per-player and min/max/average profit for every scenario at ticks 1 and 10.
(market-analysis/scenario-report)

;; Explore one scenario and inspect its allocations and utility components.
(market-analysis/run-scenario :price-pressure 10)
```

The named scenarios are available in `market-analysis/scenarios`. Tick 1 shows
the immediate result; tick 10 shows the same setup after market memory adapts.

## REPLs

### Cursive

Configure a [REPL following the Cursive documentation](https://cursive-ide.com/userguide/repl.html). Using the default "Run with IntelliJ project classpath" option will let you select an alias from the ["Clojure deps" aliases selection](https://cursive-ide.com/userguide/deps.html#refreshing-deps-dependencies).

### CIDER

Use the `cider` alias for CIDER nREPL support (run `clj -M:dev:cider`). See the [CIDER docs](https://docs.cider.mx/cider/basics/up_and_running.html) for more help.

Note that this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).

### Command Line

Run `clj -M:dev:nrepl` or `make repl`.

Note that, just like with [CIDER](#cider), this alias runs nREPL during development. To run nREPL in production (typically when the system starts), use the kit-nrepl library through the +nrepl profile as described in [the documentation](https://kit-clj.github.io/docs/profiles.html#profiles).


24×24 px neo-retro icons. Fixed 16-color master palette; maximum 6 colors per icon including outline. Hard pixel edges, no antialiasing, gradients or partial transparency. Use clear silhouettes, one shadow level and one highlight level. Maintain consistent light from the upper left. Strict technical limits; free interpretation of shape and character.

https://lospec.com/palette-list/shiny-16

#1f1723
#3c2f52
#8c7f90
#bcb0b3
#e2e2e2
#d86830
#f09548
#efd081
#b22741
#f5464c
#f79c88
#4656a5
#4995f3
#72deeb
#3ec54b
#b4e656 
