DROP INDEX game_action_uses_actor_action_tick_idx;
--;;
CREATE INDEX game_action_uses_actor_purchase_tick_idx
  ON game_action_uses (actor_game_player_id, purchase_tick DESC);
