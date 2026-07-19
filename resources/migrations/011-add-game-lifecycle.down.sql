DROP INDEX games_status_next_tick_idx;
--;;
ALTER TABLE games DROP COLUMN finished_at;
--;;
ALTER TABLE games DROP COLUMN started_at;
--;;
ALTER TABLE games DROP COLUMN host_game_player_id;
--;;
ALTER TABLE games DROP COLUMN status;
