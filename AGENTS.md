# Pommes Connection

Pommes Connection is a minimal multiplayer idle/management game written in
Clojure.

## Technical direction

- Use idiomatic, simple Clojure.
- Keep it simple, robust and minimal
- Prefer explicit data and pure functions.
- Keep abstractions minimal and easy to debug.
- code should be easy to reason about
- code should be easy to comprehend for humans
- Use Kit for application lifecycle and web plumbing.
- Use Ring and Reitit for HTTP routing.
- Use Hiccup for server-rendered HTML.
- Use next.jdbc with SQLite.
- Use SQL migrations.
- Keep the simulation independent from HTTP and persistence where practical.

## Development style

- Explain important tradeoffs briefly.
- Avoid unnecessary frameworks and architectural layers.
- Prefer plain SQL over query-building abstractions initially.
- Write example usage code in (comment ) block at the end of files
- Do not add dependencies unless they solve an immediate need.
- prefer (comment ) example over tests
- test should be minimal and as significant as possible

## Writing style
- for user facing texts consult narrative.md
