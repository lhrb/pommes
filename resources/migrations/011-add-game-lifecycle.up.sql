ALTER TABLE games ADD COLUMN status TEXT NOT NULL DEFAULT 'waiting'
  CHECK (status IN ('waiting', 'running', 'finished'));
--;;
ALTER TABLE games ADD COLUMN host_game_player_id INTEGER;
--;;
ALTER TABLE games ADD COLUMN started_at INTEGER;
--;;
ALTER TABLE games ADD COLUMN finished_at INTEGER;
--;;
UPDATE games SET next_tick_at = NULL, status = 'waiting';
--;;
CREATE INDEX games_status_next_tick_idx ON games (status, next_tick_at);
