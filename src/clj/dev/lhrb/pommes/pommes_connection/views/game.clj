(ns dev.lhrb.pommes.pommes-connection.views.game
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [hiccup.util :as hiccup]
   [ring.util.response :as response]))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- quantity [value]
  (format "%d" (long (Math/round (double (or value 0.0))))))

(def refresh-script
  "(function () {\n  const refresh = document.querySelector(\"[data-game-refresh]\");\n  if (!refresh) return;\n\n  const nextTickAt = Number(refresh.dataset.nextTickAt);\n  const serverNow = Number(refresh.dataset.serverNow);\n  const pageLoadedAt = performance.now();\n  const initialRemainingMillis = nextTickAt - serverNow;\n  const storageKey = \"pommes-connection:auto-refresh:\" + refresh.dataset.gameToken;\n  const toggle = document.getElementById(\"automatic-refresh\");\n  const countdown = document.getElementById(\"next-tick-countdown\");\n  const status = document.getElementById(\"refresh-status\");\n  const menuForm = document.getElementById(\"menu-form\");\n  let dirty = false;\n  let reloadPending = false;\n\n  try { toggle.checked = localStorage.getItem(storageKey) !== \"false\"; } catch (_) { toggle.checked = true; }\n\n  const remainingMillis = function () { return Math.max(0, initialRemainingMillis - (performance.now() - pageLoadedAt)); };\n  const remainingLabel = function () {\n    const seconds = Math.ceil(remainingMillis() / 1000);\n    const minutes = Math.floor(seconds / 60);\n    return minutes > 0 ? minutes + \"m \" + (seconds % 60) + \"s\" : seconds + \"s\";\n  };\n\n  const update = function () {\n    countdown.textContent = Number.isFinite(initialRemainingMillis) ? remainingLabel() : \"gleich\";\n    if (toggle.checked && !dirty && Number.isFinite(initialRemainingMillis) && remainingMillis() === 0 && !reloadPending) {\n      reloadPending = true;\n      window.setTimeout(function () { window.location.reload(); }, 750);\n    }\n  };\n\n  toggle.addEventListener(\"change\", function () {\n    try { localStorage.setItem(storageKey, String(toggle.checked)); } catch (_) {}\n    status.textContent = toggle.checked ? (dirty ? \"Automatisches Aktualisieren wartet, solange du schraubst.\" : \"Automatisches Aktualisieren läuft.\") : \"Automatisches Aktualisieren ist aus.\";\n  });\n\n  if (menuForm) {\n    const pauseRefresh = function () { dirty = true; status.textContent = \"Automatisches Aktualisieren wartet, solange du schraubst.\"; };\n    menuForm.addEventListener(\"input\", pauseRefresh);\n    menuForm.addEventListener(\"change\", pauseRefresh);\n  }\n\n  update();\n  window.setInterval(update, 1000);\n})();")

(defn- viewer-tick-summary [latest-tick viewer-id]
  (some #(when (= viewer-id (:player-id %)) %) (:players latest-tick)))

(defn- city-notice [game viewer-result scheduled-for]
  (cond
    scheduled-for ["good" (str "Menüänderungen sind für Tick " scheduled-for " notiert. Die Nachbarschaft spitzt die Ohren.")]
    (nil? viewer-result) ["quiet" "Verdächtig ruhig. Der erste Mittagsmob ist noch nicht eingerollt."]
    (pos? (:profit viewer-result)) ["good" "Eilmeldung vom Mittagstisch: Deine Bude hat Gewinn gemacht."]
    :else ["warning" "Merkwürdige Kritik im Lokalblatt: Beim nächsten Ansturm lieber auf die Marge schielen."]))

(defn- player-card [player viewer]
  (layout/offer-card
   [[:div.offer-card__top
     [:strong (:name player)]
     (when (= (:id player) (:id viewer)) (layout/badge "Du"))]
    [:div.stat-row [:span "Buden-Kasse"] [:strong (money (:money player))]]
    [:div.stat-row [:span "Prestige"] [:strong (quantity (:prestige player))]]]))

(defn- chronicle-panel [game events]
  (layout/panel
   "panel--tight"
   [[:div.offer-card__top
     [:h2 "Stadtchronik"]
     (layout/badge "Öffentlich")]
    (if (seq events)
      [:ol {:style "margin:0;padding-left:20px"}
       (for [{:keys [tick message]} events]
         [:li {:style "margin:0 0 10px"}
          [:strong (str "Tick " tick ": ")]
          message])]
      [:p.meta "Noch schreibt die Stadt an ihrer ersten Schlagzeile."])
    [:a.button {:href (str "/games/" (:game-token game) "/chronicle")}
     "Ganze Chronik lesen"]]))

(defn page
  [{:keys [game viewer players latest-tick scheduled-for server-now events]}]
  (let [viewer-result (viewer-tick-summary latest-tick (:id viewer))]
    (-> (layout/page
         (str (:name game) " | Pommes Connection")
         [:main
          [:header.masthead
          [:p.eyebrow "Pommes Connection · Lokalausgabe"]
           [:h1 (:name game)]
           [:p.subhead (str "Bude: " (:name viewer) " · ") (layout/badge (str "Tick " (:tick game)))]
           [:div {:data-game-refresh true
                  :data-game-token (:game-token game)
                  :data-next-tick-at (:next-tick-at game)
                  :data-server-now server-now}
            [:p.subhead "Nächster Ansturm in " [:strong {:id "next-tick-countdown"} "…"]]
            [:label {:for "automatic-refresh" :style "font-size:13px;margin:8px 0 0"}
             [:input {:id "automatic-refresh" :type "checkbox"
                      :style "display:inline-block;width:18px;min-height:18px;margin:0 8px 0 0;padding:0;vertical-align:middle"}]
             "Automatisch aktualisieren"]
            [:p.meta {:id "refresh-status" :aria-live "polite"}]]]
          (let [[kind message] (city-notice game viewer-result scheduled-for)]
            (layout/notice kind message))
          (layout/panel "panel--yellow"
                        [[:div.offer-card__top
                          [:h2 "Budenbericht"]
                          (layout/badge "Heute")]
                         (if viewer-result
                           [:div.grid.grid--three
                            (layout/stat-row "Verkäufe" (quantity (:sales viewer-result)))
                            (layout/stat-row "Gewinn" (money (:profit viewer-result)))
                            (layout/stat-row "Kasse" (money (:money viewer-result)))]
                           [:p "Noch keine Einnahmen. Bau ein Menü, bevor der erste Mob auftaucht."])])
          (layout/panel "panel--yellow"
                        [[:h2 "Gerichte & Markt"]
                         [:p "Stell deine Karte zusammen und sieh sofort, welche Kundschaft du damit erreichen kannst."]
                         [:a.button {:href (str "/games/" (:game-token game) "/dishes")}
                          "Gerichte & Markt öffnen"]])
          (layout/panel "panel--yellow"
                        [[:h2 "Aktionen"]
                         [:p "Schick Gerüchte, Rocker oder Influencer los und bring den Markt durcheinander."]
                         [:a.button {:href (str "/games/" (:game-token game) "/actions")}
                          "Aktionen öffnen"]])
          (layout/panel "panel--yellow"
                        [[:h2 "Forschung & Stammgäste"]
                         [:p "Verbessere deine Pommes und baue dir eine treue Kundschaft auf."]
                         [:a.button {:href (str "/games/" (:game-token game) "/research")}
                         "Forschung öffnen"]])
          (layout/panel "panel--yellow"
                        [[:h2 "Schwarzmarkt"]
                         [:p "Verberge Einnahmen, spare Steuern und halte den Verdacht im Griff."]
                         [:a.button {:href (str "/games/" (:game-token game) "/black-market")}
                          "Schwarzmarkt öffnen"]])
          (layout/panel "panel--yellow"
                        [[:h2 "Stadtstatistik"]
                         [:p "Sieh, wer welche Nachbarschaft erobert und wer bei Forschung und Kasse vorne liegt."]
                         [:a.button {:href (str "/games/" (:game-token game) "/stats")}
                          "Stadtstatistik öffnen"]])
          (layout/panel "panel--tight"
                        [[:h2 "Rivalenradar"]
                         [:p.meta "Gerücht: Irgendwer um die Ecke denkt schon wieder über Rabatt nach."]
                         [:div.grid.grid--two (map #(player-card % viewer) players)]])
          (chronicle-panel game events)
          (when viewer-result
            (layout/panel "panel--tight"
                          [[:h2 (str "Quittung für Tick " (:tick latest-tick))]
                           [:div.grid.grid--two
                            (layout/stat-row "Umsatz" (money (:revenue viewer-result)))
                            (layout/stat-row "Prestige" (quantity (:prestige viewer-result)))]
                           ]))
          (layout/panel "panel--quiet"
                        [[:h2 "Pinnwand"]
                         [:p.invite "Einladungslink · weitere Fritteuse gesucht: "
                          [:a {:href (str "/join/" (:invite-token game))}
                           (str "/join/" (:invite-token game))]]])
          [:script (hiccup/raw-string refresh-script)]])
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(defn chronicle-page
  [{:keys [game events page page-count]}]
  (let [navigation
        (when (> page-count 1)
          [:nav {:aria-label "Chronikseiten" :style "display:flex;gap:12px;margin-top:18px"}
           (when (pos? page)
             [:a.button {:href (str "/games/" (:game-token game) "/chronicle?page=" (dec page))}
              "Neuere Meldungen"])
           (when (< (inc page) page-count)
             [:a.button {:href (str "/games/" (:game-token game) "/chronicle?page=" (inc page))}
              "Ältere Meldungen"])])]
    (-> (layout/page
         (str (:name game) " | Stadtchronik")
         [:main
          [:header.masthead
           [:p.eyebrow "Pommes Connection · Lokalausgabe"]
           [:h1 "Stadtchronik"]
           [:p.subhead (:name game) " · Was die Stadt nie vergessen wird."]
           [:p.meta [:a {:href (str "/games/" (:game-token game))} "← Zur Budenübersicht"]]]
          (layout/panel
           "panel--yellow"
           [[:h2 "Die Schlagzeilen"]
            (if (seq events)
              [:ol {:style "margin:0;padding-left:20px"}
               (for [{:keys [tick message]} events]
                 [:li {:style "margin:0 0 14px"}
                  [:strong (str "Tick " tick " · ")]
                  message])]
              [:p "Noch keine Ereignisse. Die Stadt sammelt Material."])
            navigation])])
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (page {:game {:id 1 :name "Lunch rush" :tick 0} :viewer {:id 1 :name "Ada"}})
  ;; (chronicle-page {:game {:name "Lunch" :game-token "ABC123"} :events [] :page 0 :page-count 0})
  )
