ALTER TABLE games ADD COLUMN next_tick_at INTEGER;
--;;

UPDATE games
SET next_tick_at = (CAST(strftime('%s', 'now') AS INTEGER) * 1000) + 30000
WHERE next_tick_at IS NULL;
