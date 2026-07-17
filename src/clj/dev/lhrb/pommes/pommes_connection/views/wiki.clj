(ns dev.lhrb.pommes.pommes-connection.views.wiki
  (:require [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
            [ring.util.response :as response]))

(defn page [{:keys [game viewer hx?]}]
  (-> (layout/game-response (str (:name game) " | Budenwissen") game viewer :wiki
                            [[:header.screen-head (layout/icon "nav_wiki" "") [:div [:h1 "Budenwissen"] [:p "Ein Merkblatt, das leider verbindlich wirkt."]]]
                             (layout/panel "panel--yellow" [[:h2 "Der Ansturm"] [:p "Alle 30 Sekunden wird abgerechnet. Karte, Preise und die Laune der Straße entscheiden über deine Portionen."]])
                             (layout/panel [[:h2 "Karte & Markt"] [:p "Menüänderungen gelten zum nächsten Tick. Die Prognose zeigt, welche Kundschaft vermutlich bei dir landet."]])
                             (layout/panel [[:h2 "Aktionen"] [:p "Aktionen kosten Geld, treffen ab dem nächsten Tick ein und haben eine Abklingzeit. Manche helfen, manche stören. Die Straße unterscheidet da nicht immer sauber."]])
                             (layout/panel [[:h2 "Forschung & Schwarzmarkt"] [:p "Forschung macht Stammgäste zuverlässiger. Der Schwarzmarkt spart Steuern, sammelt aber Verdacht. Ein Kontrolleur hat keinen Humor und einen Stempel."]])
                             (layout/panel "panel--quiet" [[:h2 "Prestige"] [:p "Prestige zeigt, welche Bude die Stadt für eine ernsthafte Angelegenheit hält. Das kann sich jederzeit ändern."]])] hx?)
      response/response (response/content-type "text/html; charset=utf-8")))
