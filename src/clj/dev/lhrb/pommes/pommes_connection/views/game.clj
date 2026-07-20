(ns dev.lhrb.pommes.pommes-connection.views.game
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(def demographic-icons
  {:students "dem_student"
   :families "dem_families"
   :yuppies "dem_yuppi"
   :office-workers "dem_worker"
   :retirees "dem_retiree"
   :foodies "dem_foodie"})

(def chart-colors ["#f5464c" "#4995f3" "#3ec54b" "#f09548" "#b22741" "#72deeb" "#b4e656" "#3c2f52"])

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- quantity [value]
  (format "%d" (long (Math/round (double (or value 0.0))))))

(defn- viewer-tick-summary [latest-tick viewer-id]
  (some #(when (= viewer-id (:player-id %)) %) (:players latest-tick)))

(defn- demographic-icon [id alt]
  [:img.demographic-icon {:src (str "/assets/icons/" (get demographic-icons id "dem_foodie") "_48.png")
                          :alt alt :width 32 :height 32}])

(defn- city-notice [viewer-result]
  (cond
    (nil? viewer-result) ["quiet" "Vor der Bude ist es still. Das ist kein Frieden, nur die Zeit vor einer Bestellung."]
    (pos? (:profit viewer-result)) ["good" "Die Kasse hat sich bewegt. Nun muss entschieden werden, ob das Methode oder ein bedauerlicher Zufall war."]
    :else ["warning" "Der letzte Ansturm war gegen die Kasse. Die Karte verlangt nach einer sachlichen Korrektur."]))

(defn- navigation-link [game path label]
  [:a.button {:href (str "/games/" (:game-token game) path)
              :hx-get (str "/games/" (:game-token game) path)
              :hx-target "#game-screen" :hx-swap "outerHTML" :hx-push-url "true"}
   label])

(defn- action-panel [game viewer-result]
  (let [[path title copy label]
        (if (or (nil? viewer-result) (not (pos? (:profit viewer-result))))
          ["/dishes" "Die Karte duldet keinen Aufschub"
           "Die nächste Portion wird nach deiner Entscheidung verkauft. Oder nicht verkauft, was ebenfalls eine Entscheidung wäre."
           "Karte ändern"]
          ["/actions" "Die Straße ist in Bewegung"
           "Der Betrieb läuft. Das ist der Augenblick, in dem andere Buden unvorsichtig werden könnten."
           "Aktionen prüfen"])]
    (layout/panel "panel--yellow action-prompt"
                  [[:h2 title]
                   [:p copy]
                   (navigation-link game path label)])))

(defn- winner [entries]
  (when-let [best (first (sort-by (juxt (comp - :sales) :player-id :dish-id) entries))]
    (when (pos? (:sales best)) best)))

(defn- dish-by-id [player dish-id]
  (some #(when (= (str dish-id) (str (:id %))) %) (:dishes player)))

(defn- best-selling-dishes [players latest-tick viewer-id]
  (let [players-by-id (into {} (map (juxt :id identity) players))
        entries (mapcat (fn [{:keys [player-id dish-sales]}]
                          (for [[dish-id sales] dish-sales]
                            {:player-id player-id :dish-id dish-id :sales sales}))
                        (:players latest-tick))
        decorate (fn [entry]
                   (let [player (get players-by-id (:player-id entry))
                         dish (dish-by-id player (:dish-id entry))]
                     (assoc entry :player-name (:name player) :dish-name (:name dish))))]
    {:own (some->> entries (filter #(= viewer-id (:player-id %))) winner decorate)
     :town (some-> entries winner decorate)}))

(defn- dish-result [label result include-player?]
  [:div.dish-result
   [:span label]
   (if result
     [:strong (str (:dish-name result) (when include-player? (str " · " (:player-name result))))]
     [:strong "Noch ohne Urteil"])
   (when result [:small (str (quantity (:sales result)) " Portionen")])])

(defn- donut-slices [entries]
  (let [total (reduce + 0 (map :population entries))]
    (when (pos? total)
      (second
       (reduce (fn [[offset slices] entry]
                 (let [size (* 100.0 (/ (double (:population entry)) total))]
                   [(+ offset size) (conj slices (assoc entry :offset offset :size size))]))
               [0.0 []]
               entries)))))

(defn- demographic-donut [city]
  (let [entries (:demographics city)
        slices (donut-slices entries)
        total (reduce + 0 (map :population entries))]
    (layout/panel
     "panel--tight city-demographics"
     [[:h2 "Wer heute in der Stadt ist"]
      [:p.meta "Die Gruppen verteilen sich, als hätten sie es untereinander abgesprochen."]
      (if (pos? total)
        [:div.city-demographics__body
         [:svg.city-donut {:viewBox "0 0 120 120" :role "img"
                           :aria-label "Anteile der Kundengruppen in der Stadt"}
          [:title "Anteile der Kundengruppen in der Stadt"]
          [:circle.city-donut__track {:cx 60 :cy 60 :r 46}]
          (map-indexed
           (fn [index {:keys [offset size]}]
             [:circle.city-donut__slice {:cx 60 :cy 60 :r 46 :pathLength 100
                                         :stroke (nth chart-colors (mod index (count chart-colors)))
                                         :stroke-dasharray (str size " " (- 100.0 size))
                                         :stroke-dashoffset (- offset)}])
           slices)]
         [:ul.city-demographics__legend
          (map-indexed (fn [index {:keys [id population]}]
                         [:li
                          [:span.city-demographics__swatch {:style (str "background:" (nth chart-colors (mod index (count chart-colors))))}]
                          (demographic-icon id (str (name id) ": " (quantity population)))
                          [:strong (layout/number (quantity population))]])
                       entries)]]
        [:p.meta "Die Stadt zählt noch nach. Niemand möchte die Verantwortung für die letzte Zahl übernehmen."])])))

(defn- profit-chart [players latest-tick]
  (let [latest-by-player (into {} (map (juxt :player-id identity) (:players latest-tick)))
        entries (->> players
                     (map (fn [player]
                            {:id (:id player) :name (:name player)
                             :profit (double (get-in latest-by-player [(:id player) :profit] 0.0))}))
                     (sort-by (juxt (comp - :profit) :name))
                     vec)
        maximum (apply max 0.0 (map :profit entries))
        minimum (apply min 0.0 (map :profit entries))
        range-value (max 1.0 (- maximum minimum))
        zero-x (+ 16 (* 168 (/ (- 0.0 minimum) range-value)))]
    (layout/panel
     "panel--tight profit-chart"
     [[:h2 "Gewinnlage der Buden"]
      [:p.meta "Der letzte Ansturm wurde abgerechnet. Die Beträge sind nicht verhandelbar, nur ihre Deutung."]
      (if (seq entries)
        [:div.profit-chart__rows
         (map-indexed
          (fn [index {:keys [name profit]}]
            (let [value-x (+ 16 (* 168 (/ (- profit minimum) range-value)))
                  left (min zero-x value-x)
                  width (max 1 (Math/abs (- value-x zero-x)))]
              [:div {:class (str "profit-chart__row" (when (zero? index) " profit-chart__row--leader"))}
               [:span.profit-chart__name name]
               [:svg {:viewBox "0 0 200 28" :role "img" :aria-label (str name ": " (money profit) " Gewinn")}
                [:line.profit-chart__zero {:x1 zero-x :x2 zero-x :y1 2 :y2 26}]
                [:rect {:x left :y 5 :width width :height 18
                        :fill (nth chart-colors (mod index (count chart-colors)))}]]
               [:strong (layout/number (money profit))]]))
          entries)]
        [:p.meta "Noch keine Bude hat einen Ansturm abgerechnet."])])))

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
      [:p.meta "Noch fehlt der Stadt ein Vorfall, den sie mit ausreichender Gewissheit falsch erzählen kann."])
    (navigation-link game "/chronicle" "Chronik aufschlagen")]))

(def prestige-labels
  {:demographic-market-share "Marktanteile"
   :best-selling-dish "Meistverkauftes Gericht"
   :portions "Portionen"
   :profit "Gewinn"
   :regulars "Stammkundschaft"
   :research-leadership "Forschung"
   :black-market-leadership "Schwarzmarkt"
   :actions "Aktionen"
   :audits "Prüfungen"})

(defn- waiting-panel [game viewer players anti-forgery-token]
  (layout/panel
   "panel--yellow"
   [[:h2 "Die Straße zählt ihre Buden"]
    [:p (str (count players) " von 3 Buden sind eingetragen. Erst dann darf die Woche beginnen.")]
    (if (= (:id viewer) (:host-game-player-id game))
      [:form {:method "post" :action (str "/games/" (:game-token game) "/start")}
       [:input {:type "hidden" :name "__anti-forgery-token" :value anti-forgery-token}]
       (layout/button {:disabled (not= 3 (count players))} "Sieben Tage eröffnen")]
      [:p.meta "Die erste Bude besitzt das amtliche Recht, auf den Knopf zu drücken."])]))

(defn- prestige-panel [viewer]
  (let [sources (get-in viewer [:game-stats :prestige-by-source])
        latest (get-in viewer [:game-stats :latest-score-round])]
    (layout/panel
     "panel--quiet"
     [[:h2 "Wofür man dich kennt"]
      (if (seq sources)
        [:div.grid.grid--two
         (for [[source points] (sort-by (comp str key) sources)]
           (layout/stat-row (get prestige-labels source (name source))
                            (layout/number points)))]
        [:p.meta "Noch ist dein Ruf ein ordentlich gefaltetes, leeres Blatt."])
      (when latest
        [:p.meta (str "Letzte Wertung bei Tick " (:tick latest) ": "
                      (:total latest) " Prestige.")])])))

(defn- finished-panel [players]
  (let [ranking (sort-by (juxt (comp - :prestige) :name) players)
        winner (first ranking)]
    (layout/panel
     "panel--yellow"
     [[:h2 "Die Goldene Pommes ist vergeben"]
      [:p (str (:name winner) " gewinnt mit " (quantity (:prestige winner))
               " Prestige. Die übrigen Buden erkennen das Ergebnis an, soweit es ihre Würde erlaubt.")]
      [:ol (for [player ranking]
             [:li [:strong (:name player)] " · " (quantity (:prestige player)) " Prestige"])]])))

(defn page
  [{:keys [game viewer players city latest-tick events hx? server-now anti-forgery-token]}]
  (let [viewer-result (viewer-tick-summary latest-tick (:id viewer))
        dishes (when latest-tick (best-selling-dishes players latest-tick (:id viewer)))]
    (-> (layout/game-response
         (str (:name game) " | Pommes Connection") game viewer :game
         [[:header.screen-head
           [:div [:h1 "Budenlage"] [:p (:name viewer) " hält die Schicht zusammen."]]]
          (let [[kind message] (city-notice viewer-result)]
            (layout/notice kind message))
          (case (:status game)
            :waiting (waiting-panel game viewer players anti-forgery-token)
            :finished (finished-panel players)
            (action-panel game viewer-result))
          (layout/panel "panel--yellow"
                        [[:div.offer-card__top
                          [:h2 "Budenbericht"]
                          (layout/badge "Letzter Ansturm")]
                         (if viewer-result
                           [:div.grid.grid--two
                            (layout/stat-row "Verkäufe" (layout/number (quantity (:sales viewer-result))))
                            (layout/stat-row "Umsatz" (layout/number (money (:revenue viewer-result))))
                            (layout/stat-row "Gewinn" (layout/number (money (:profit viewer-result))))
                            (layout/stat-row "Kasse" (layout/number (money (:money viewer-result))))
                            (dish-result "Dein meistverkauftes Gericht" (:own dishes) false)]
                           [:p "Die Fritteuse wartet auf ihren ersten Bericht. Eine Karte wäre ein Anfang, auch wenn die Stadt das nicht so nennen würde."])])
          (when latest-tick
            (layout/panel "panel--quiet market-summary"
                          [[:h2 "Was die Stadt bestellt"]
                           [:p.meta "Ein Gericht hat sich im letzten Ansturm durchgesetzt. Die Gründe liegen bei der Kundschaft und sind deshalb nicht zuverlässig zu ermitteln."]
                           (dish-result "Meistverkauft in der Stadt" (:town dishes) true)]))
          (demographic-donut city)
          (when latest-tick (profit-chart players latest-tick))
          (prestige-panel viewer)
          (chronicle-panel game events)
          (when (= :waiting (:status game))
            (layout/panel "panel--quiet"
                          [[:h2 "Pinnwand"]
                           [:p "Ein weiterer Betreiber wird gesucht. Die Stadt behauptet, sie brauche Auswahl; vermutlich braucht sie Gesprächsstoff."]
                           [:p.invite "Einladungslink: "
                            [:a {:href (str "/join/" (:invite-token game))}
                             (str "/join/" (:invite-token game))]]]))]
         hx? server-now)
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(defn chronicle-page
  [{:keys [game viewer events page page-count hx?]}]
  (let [navigation
        (when (> page-count 1)
          [:nav {:aria-label "Chronikseiten" :style "display:flex;gap:12px;margin-top:18px"}
           (when (pos? page)
             [:a.button {:href (str "/games/" (:game-token game) "/chronicle?page=" (dec page))}
              "Neuere Meldungen"])
           (when (< (inc page) page-count)
             [:a.button {:href (str "/games/" (:game-token game) "/chronicle?page=" (inc page))}
              "Ältere Meldungen"])])]
    (-> (layout/game-response
         (str (:name game) " | Stadtchronik") game viewer :chronicle
         [[:header.screen-head (layout/icon "nav_chronic" "") [:div [:h1 "Stadtchronik"] [:p "Was die Stadt nie vergessen wird."]]]
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
            navigation])] hx?)
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (page {:game {:name "Lunch" :game-token "ABC123"} :viewer {:id 1 :name "Ada"}
  ;;        :players [] :city {:demographics []}})
  ;; (chronicle-page {:game {:name "Lunch" :game-token "ABC123"} :events [] :page 0 :page-count 0})
  )
