(ns dev.lhrb.pommes.pommes-connection.views.dishes
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [hiccup.util :as hiccup]
   [ring.util.response :as response]))

(defn setup-options []
  (let [catalog (:catalog game-data/game-data)]
    {:interiors (keys (:interiors catalog))
     :sizes (keys (:sizes catalog))
     :seasonings (keys (:seasonings catalog))
     :toppings (keys (:toppings catalog))
     :catalog catalog
     :dish-count (get-in game-data/game-data [:setup :dish-count])}))

(defn- label [value]
  ({:small "Klein" :medium "Mittel" :large "Groß" :extra-large "Riesenportion"
    :salt "Salz" :pommesgewuerz "Pommesgewürz" :none "Ohne" :ketchup "Ketchup"
    :mayo "Mayo" :ketchup-mayo "Rot-Weiß" :special "Spezialsauce" :sate "Satésauce"
    :curry-ketchup "Curryketchup" :garlic-sauce "Knoblauchsauce" :classic "Klassisch"
    :modern "Modern" :cozy "Gemütlich" :street-food "Imbissbude" :premium "Schicker Laden"
    :students "Studierende" :families "Familien" :yuppies "Yuppies"
    :office-workers "Büromenschen" :retirees "Rentnerinnen und Rentner"
    :foodies "Feinschmeckerinnen und Feinschmecker"}
   value (-> value name (str/replace #"-" " ") str/capitalize)))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- quantity [value] (format "%d" (long (Math/round (double (or value 0.0))))))
(defn- percentage [value] (str (format "%.1f" (* 100.0 (double (or value 0.0)))) "%"))
(defn- signed [value] (str (if (pos? value) "+" "") (quantity value)))

(defn- select-input [field value options cost]
  [:select {:name field :id field}
   (for [option options]
     (let [option-cost (when cost (cost option))]
       [:option (cond-> {:value (name option) :selected (= option value)}
                  (some? option-cost) (assoc :data-cost (str option-cost)))
        (str (label option) (when (some? option-cost) (str " (" (money option-cost) ")")))]))])

(defn- dish-cost [catalog dish]
  (+ (double (get-in catalog [:sizes (get-in dish [:pommes :size]) :cost] 0.0))
     (double (get-in catalog [:seasonings (get-in dish [:pommes :seasoning]) :cost] 0.0))
     (double (get-in catalog [:toppings (:topping dish) :cost] 0.0))))

(defn- dish-field [options index dish errors]
  (let [prefix (str "dish-" index "-") catalog (:catalog options)]
    [:fieldset {:data-dish true}
     [:legend (str "Gericht " index)]
     [:div.grid
      [:label {:for (str prefix "name")} "Name"
       [:input {:name (str prefix "name") :id (str prefix "name") :required true :value (or (:name dish) "")}]]
      [:label {:for (str prefix "size")} "Größe"
       (select-input (str prefix "size") (get-in dish [:pommes :size]) (:sizes options)
                     #(get-in catalog [:sizes % :cost]))]
      [:label {:for (str prefix "seasoning")} "Würzung"
       (select-input (str prefix "seasoning") (get-in dish [:pommes :seasoning]) (:seasonings options)
                     #(get-in catalog [:seasonings % :cost]))]
      [:label {:for (str prefix "topping")} "Sauce"
       (select-input (str prefix "topping") (:topping dish) (:toppings options)
                     #(get-in catalog [:toppings % :cost]))]
      [:label {:for (str prefix "price")} "Preis"
       [:input {:name (str prefix "price") :id (str prefix "price") :type "number" :step "0.01"
                :min "0" :required true :value (or (:price dish) "")}]]]
     [:p "Wareneinsatz: " [:strong {:data-cost-total true} (money (dish-cost catalog dish))]]
     (when-let [dish-errors (seq (get errors index))]
       [:ul.errors (for [error dish-errors] [:li error])])]))

(defn forecast-panel [forecast]
  (layout/panel "panel--quiet"
                [[:h2 "Marktprognose · nächster Ansturm"]
                 [:p.meta "Die Vorschau vergleicht deine aktuelle Karte mit den Änderungen, die gerade im Formular stehen."]
                 [:table.forecast-table {:data-forecast-table true}
                  [:thead [:tr [:th "Kundschaft"] [:th.numeric "Mögliche Portionen"]
                           [:th "Bestes Gericht"] [:th.numeric "Du gewinnst"]
                           [:th.numeric "Kundenanteil"] [:th.numeric "Änderung"]]]
                  [:tbody
                   (for [{:keys [demographic-id potential-demand dish-name sales share sales-change share-change]} forecast]
                     [:tr [:td (label demographic-id)] [:td.numeric (quantity potential-demand)]
                      [:td (or dish-name "–")] [:td.numeric (quantity sales)]
                      [:td.numeric (percentage share)]
                      [:td.numeric (str (signed sales-change) " · " (format "%+.1f" (* 100.0 share-change)) " PP")]])]]
                 [:div.forecast-cards
                  (for [{:keys [demographic-id potential-demand dish-name sales share sales-change share-change]} forecast]
                    (layout/offer-card
                     [[:div.offer-card__top
                       [:h3 (label demographic-id)]
                       (layout/badge (str (quantity potential-demand) " mögliche Portionen"))]
                      [:div.stat-row [:span "Bestes Gericht"] [:strong (or dish-name "–")]]
                      [:div.stat-row [:span "Du gewinnst"] [:strong (quantity sales)]]
                      [:div.stat-row [:span "Kundenanteil"] [:strong (percentage share)]]
                      [:div.stat-row [:span "Änderung"]
                       [:strong (str (signed sales-change) " · "
                                     (format "%+.1f" (* 100.0 share-change)) " PP")]]]))]]))

(defn- latest-market-panel [{:keys [city players latest-tick]}]
  (when latest-tick
    (let [by-player (into {} (map (juxt :player-id identity) (:players latest-tick)))]
      (layout/panel "panel--tight"
                    [[:h2 (str "Marktanteile im letzten Tick · " (:tick latest-tick))]
                     [:p.meta "Markiert ist jeweils die Bude mit den meisten verkauften Portionen."]
                     [:table
                      [:thead (into [:tr [:th "Kundschaft"]]
                                    (for [player players] [:th.numeric (:name player)]))]
                      [:tbody
                       (for [{:keys [id]} (:demographics city)
                             :let [sales (map #(get-in by-player [(:id %) :demographic-sales id] 0) players)
                                   leader (apply max 0 sales)]]
                         (into [:tr [:td (label id)]]
                               (map (fn [player sold]
                                      [:td.numeric
                                       (if (= sold leader) [:strong (quantity sold)] (quantity sold))])
                                    players sales)))]]]))))

(defn page [{:keys [game viewer players city latest-tick form errors scheduled-for anti-forgery-token forecast hx?]}]
  (let [options (setup-options)
        dishes (take (:dish-count options) (concat (or (:dishes form) (:dishes viewer)) (repeat {})))]
    (-> (layout/game-response
         (str "Gerichte & Markt | " (:name game)) game viewer :dishes
         [[:header.screen-head (layout/icon "nav_dishes" "") [:div [:h1 "Gerichte & Markt"] [:p "Die Karte entscheidet, wer stehen bleibt."]]]
          (when scheduled-for (layout/notice "good" (str "Menüänderungen sind für Tick " scheduled-for " notiert.")))
          [:div {:id "market-forecast"} (forecast-panel forecast)]
          (latest-market-panel {:city city :players players :latest-tick latest-tick})
          [:section {:id "menu-editor" :class "panel panel--yellow"}
                         [:h2 "Morgenkarte drucken"]
                         [:p "Änderungen gelten ab dem nächsten Ansturm. Die Marktprognose reagiert direkt auf deine Eingaben."]
                         (when (seq errors) [:p.errors "Bitte korrigiere die markierten Menüdetails."])
                         [:form {:id "menu-form" :method "post"
                                 :action (str "/games/" (:game-token game) "/dishes/setup")
                                 :hx-post (str "/games/" (:game-token game) "/dishes/preview")
                                 :hx-trigger "input changed delay:350ms, change"
                                 :hx-target "#market-forecast" :hx-swap "innerHTML"}
                          (when anti-forgery-token [:input {:type "hidden" :name "__anti-forgery-token" :value anti-forgery-token}])
                          [:fieldset [:legend "Innenleben"]
                           [:label {:for "interior"} "Budenstimmung"
                            (select-input "interior" (or (:interior form) (:interior viewer)) (:interiors options) nil)]
                           (when-let [interior-errors (seq (:interior errors))]
                             [:ul.errors (for [error interior-errors] [:li error])])]
                          (map-indexed (fn [index dish] (dish-field options (inc index) dish errors)) dishes)
                          (layout/button {:type "button" :hx-post (str "/games/" (:game-token game) "/dishes/setup")
                                          :hx-include "#menu-form" :hx-target "#game-screen" :hx-swap "outerHTML"}
                                         "Für den nächsten Tick eintragen")]]]
         hx?)
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (forecast-panel [])
  )
