CREATE TABLE player_regulars (
  game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  demographic_id TEXT NOT NULL,
  count INTEGER NOT NULL DEFAULT 0 CHECK (count >= 0),
  PRIMARY KEY (game_player_id, demographic_id)
);
--;;
CREATE TABLE player_research (
  game_player_id INTEGER NOT NULL REFERENCES game_players(id) ON DELETE CASCADE,
  research_id TEXT NOT NULL,
  level INTEGER NOT NULL DEFAULT 0 CHECK (level >= 0),
  PRIMARY KEY (game_player_id, research_id)
);
--;;
CREATE INDEX player_research_research_level_idx
  ON player_research (research_id, level DESC);
