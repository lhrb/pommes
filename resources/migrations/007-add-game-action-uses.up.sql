CREATE TABLE game_action_uses (
  id INTEGER PRIMARY KEY,
  game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  actor_game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  target_game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  action_id TEXT NOT NULL,
  cost REAL NOT NULL CHECK (cost >= 0),
  purchase_tick INTEGER NOT NULL CHECK (purchase_tick >= 0),
  apply_at_tick INTEGER NOT NULL CHECK (apply_at_tick > 0),
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
--;;
CREATE INDEX game_action_uses_actor_action_tick_idx
  ON game_action_uses (actor_game_player_id, action_id, purchase_tick DESC);
--;;
CREATE INDEX game_action_uses_target_idx
  ON game_action_uses (target_game_player_id);
