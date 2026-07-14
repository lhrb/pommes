CREATE TABLE games (
  id INTEGER PRIMARY KEY,
  name TEXT NOT NULL UNIQUE,
  tick INTEGER NOT NULL DEFAULT 0 CHECK (tick >= 0),
  market_state TEXT NOT NULL DEFAULT '{}',
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
--;;

CREATE TABLE game_players (
  id INTEGER PRIMARY KEY,
  game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  player_id INTEGER NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  money REAL NOT NULL DEFAULT 20.0,
  prestige REAL NOT NULL DEFAULT 0.0,
  interior TEXT NOT NULL,
  active_effects TEXT NOT NULL DEFAULT '[]',
  UNIQUE (game_id, player_id)
);
--;;

CREATE TABLE player_dishes (
  id INTEGER PRIMARY KEY,
  game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  size TEXT NOT NULL,
  portion REAL NOT NULL CHECK (portion > 0),
  seasoning TEXT NOT NULL,
  topping TEXT NOT NULL,
  price REAL NOT NULL CHECK (price >= 0)
);
--;;

CREATE TABLE scheduled_actions (
  id INTEGER PRIMARY KEY,
  game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  kind TEXT NOT NULL,
  payload TEXT NOT NULL,
  apply_at_tick INTEGER NOT NULL CHECK (apply_at_tick > 0),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE (game_player_id, kind, apply_at_tick)
);
--;;

CREATE TABLE game_ticks (
  id INTEGER PRIMARY KEY,
  game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  tick INTEGER NOT NULL CHECK (tick > 0),
  completed_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
  summary TEXT NOT NULL,
  UNIQUE (game_id, tick)
);
--;;

CREATE INDEX player_dishes_game_player_id_idx ON player_dishes (game_player_id);
--;;
CREATE INDEX scheduled_actions_game_player_tick_idx
  ON scheduled_actions (game_player_id, apply_at_tick);
--;;
CREATE INDEX game_ticks_game_id_tick_idx ON game_ticks (game_id, tick);
