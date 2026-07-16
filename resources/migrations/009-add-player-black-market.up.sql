CREATE TABLE player_black_market (
  game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  item_id TEXT NOT NULL,
  level INTEGER NOT NULL DEFAULT 0 CHECK (level >= 0),
  PRIMARY KEY (game_player_id, item_id)
);
