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
  prestige INTEGER NOT NULL DEFAULT 0,
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
  seasoning TEXT NOT NULL,
  topping TEXT NOT NULL,
  price REAL NOT NULL CHECK (price >= 0)
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
CREATE INDEX game_ticks_game_id_tick_idx ON game_ticks (game_id, tick);
