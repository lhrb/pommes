CREATE TABLE game_events (
  id INTEGER PRIMARY KEY,
  game_id INTEGER NOT NULL REFERENCES games(id) ON DELETE CASCADE,
  tick INTEGER NOT NULL CHECK (tick > 0),
  kind TEXT NOT NULL,
  message TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);
--;;
CREATE INDEX game_events_game_id_tick_idx ON game_events (game_id, tick DESC, id DESC);
