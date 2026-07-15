ALTER TABLE players ADD COLUMN password_hash TEXT;
--;;

ALTER TABLE games ADD COLUMN game_token TEXT;
--;;

ALTER TABLE games ADD COLUMN invite_token TEXT;
--;;

ALTER TABLE game_players ADD COLUMN player_name TEXT;
--;;

UPDATE game_players
SET player_name = (SELECT name FROM players WHERE players.id = game_players.player_id);
--;;

UPDATE games
SET game_token = substr(hex(randomblob(3)), 1, 6),
    invite_token = substr(hex(randomblob(3)), 1, 6)
WHERE game_token IS NULL OR invite_token IS NULL;
--;;

CREATE UNIQUE INDEX games_game_token_idx ON games (game_token);
--;;

CREATE UNIQUE INDEX games_invite_token_idx ON games (invite_token);
--;;

CREATE UNIQUE INDEX game_players_game_player_name_idx ON game_players (game_id, player_name);
