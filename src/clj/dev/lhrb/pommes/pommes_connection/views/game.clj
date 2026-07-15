(ns dev.lhrb.pommes.pommes-connection.views.game
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
  (-> value name (str/replace #"-" " ") str/capitalize))

(defn- money [value]
  (format "€%.2f" (double (or value 0.0))))

(defn- quantity [value]
  (format "%d" (long (Math/round (double (or value 0.0))))))

(defn- select-input [field value options cost]
  [:select {:name field :id field}
   (for [option options]
     (let [option-cost (when cost (cost option))]
       [:option (cond-> {:value (name option) :selected (= option value)}
                  (some? option-cost) (assoc :data-cost (str option-cost)))
        (str (label option)
             (when (some? option-cost) (str " (" (money option-cost) ")")))]))])

(defn- text-input [field value attributes]
  [:input (merge {:name field :id field :value (or value "")} attributes)])

(defn- dish-cost [catalog dish]
  (+ (double (get-in catalog [:sizes (get-in dish [:pommes :size]) :cost] 0.0))
     (double (get-in catalog [:seasonings (get-in dish [:pommes :seasoning]) :cost] 0.0))
     (double (get-in catalog [:toppings (:topping dish) :cost] 0.0))))

(defn- dish-field [options index dish errors]
  (let [prefix (str "dish-" index "-")
        pommes (:pommes dish)
        catalog (:catalog options)]
    [:fieldset {:data-dish true}
     [:legend (str "Dish " index)]
     [:div.grid
      [:label {:for (str prefix "name")} "Name"
       (text-input (str prefix "name") (:name dish) {:required true})]
      [:label {:for (str prefix "size")} "Size"
       (select-input (str prefix "size") (:size pommes) (:sizes options)
                     #(get-in catalog [:sizes % :cost]))]
      [:label {:for (str prefix "seasoning")} "Seasoning"
       (select-input (str prefix "seasoning") (:seasoning pommes) (:seasonings options)
                     #(get-in catalog [:seasonings % :cost]))]
      [:label {:for (str prefix "topping")} "Topping"
       (select-input (str prefix "topping") (:topping dish) (:toppings options)
                     #(get-in catalog [:toppings % :cost]))]
      [:label {:for (str prefix "price")} "Price"
       (text-input (str prefix "price") (:price dish)
                   {:type "number" :step "0.01" :min "0" :required true})]]
     [:p "Ingredient cost: " [:strong {:data-cost-total true} (money (dish-cost catalog dish))]]
     (when-let [dish-errors (seq (get errors index))]
       [:ul.errors (for [error dish-errors] [:li error])])]))

(def cost-script
  "document.querySelectorAll(\"[data-dish]\").forEach(function (dish) {\n  const updateCost = function () {\n    const total = Array.from(dish.querySelectorAll(\"select option:checked\")).reduce(function (sum, option) { return sum + Number(option.dataset.cost || 0); }, 0);\n    dish.querySelector(\"[data-cost-total]\").textContent = \"€\" + total.toFixed(2);\n  };\n  dish.querySelectorAll(\"select\").forEach(function (input) { input.addEventListener(\"change\", updateCost); });\n  updateCost();\n});")

(defn- viewer-tick-summary [latest-tick viewer-id]
  (some #(when (= viewer-id (:player-id %)) %) (:players latest-tick)))

(defn- city-notice [game viewer-result scheduled-for]
  (cond
    scheduled-for ["good" (str "Menu changes filed for tick " scheduled-for ". The neighborhood is watching.")]
    (nil? viewer-result) ["quiet" "Suspiciously quiet. The first lunch crowd has not arrived."]
    (pos? (:profit viewer-result)) ["good" "Lunch rush report: your counter cleared a profit."]
    :else ["warning" "Strange review in the local paper: mind the margin at the next rush."]))

(defn- demand-card [{:keys [id population eat-rate]}]
  (let [demand (* population eat-rate)]
    (layout/offer-card
     [[:div.offer-card__top
       [:h3 (label id)]
       (layout/badge (str (quantity demand) " plates"))]
      [:p.meta (str population " locals · possible hunger per tick")]
      (layout/meter (* 100 (/ demand 60.0)))])))

(defn- player-card [player viewer]
  (layout/offer-card
   [[:div.offer-card__top
     [:strong (:name player)]
     (when (= (:id player) (:id viewer)) (layout/badge "You"))]
    [:div.stat-row [:span "Counter cash"] [:strong (money (:money player))]]
    [:div.stat-row [:span "Prestige"] [:strong (quantity (:prestige player))]]]))

(defn page
  [{:keys [game viewer players city latest-tick form errors scheduled-for anti-forgery-token]}]
  (let [options (setup-options)
        viewer-result (viewer-tick-summary latest-tick (:id viewer))
        dishes (take (:dish-count options) (concat (or (:dishes form) (:dishes viewer)) (repeat {})))]
    (-> (layout/page
         (str (:name game) " | Pommes Connection")
         [:main
          [:header.masthead
           [:p.eyebrow "Pommes Connection · local edition"]
           [:h1 (:name game)]
           [:p.subhead (str "Counter: " (:name viewer) " · ") (layout/badge (str "Tick " (:tick game)))]]
          (let [[kind message] (city-notice game viewer-result scheduled-for)]
            (layout/notice kind message))
          (layout/panel "panel--yellow"
                        [[:div.offer-card__top
                          [:h2 "Counter report"]
                          (layout/badge "Today")]
                         (if viewer-result
                           [:div.grid.grid--three
                            (layout/stat-row "Sales" (quantity (:sales viewer-result)))
                            (layout/stat-row "Profit" (money (:profit viewer-result)))
                            (layout/stat-row "Cash" (money (:money viewer-result)))]
                           [:p "No takings yet. Set a menu before the first crowd appears."])])
          (layout/panel "panel--tight"
                        [[:h2 "Current demographics · the block is talking"]
                         [:div.grid.grid--three (map demand-card (:demographics city))]])
          (layout/panel "panel--tight"
                        [[:h2 "Rival watch"]
                         [:p.meta "Rumor: someone nearby is always considering a discount."]
                         [:div.grid.grid--two (map #(player-card % viewer) players)]])
          (when viewer-result
            (layout/panel "panel--tight"
                          [[:h2 (str "Tick " (:tick latest-tick) " receipt")]
                           [:div.grid.grid--two
                            (layout/stat-row "Revenue" (money (:revenue viewer-result)))
                            (layout/stat-row "Prestige" (quantity (:prestige viewer-result)))]
                           [:h3 "Sales by demographic"]
                           [:table
                            [:thead [:tr [:th "Street crowd"] [:th.numeric "Orders"]]]
                            [:tbody
                             (for [{:keys [id]} (:demographics city)]
                               [:tr [:td (label id)]
                                [:td.numeric (quantity (get (:demographic-sales viewer-result) id))]])]]]))
          (layout/panel "panel--yellow"
                        [[:div.offer-card__top
                          [:h2 "Print tomorrow's menu"]
                          (layout/badge "Next tick")]
                         [:p "Changes wait for the next rush. Keep it short; let the fries speak."]
                         (when (seq errors)
                           [:p.errors "Please correct the marked menu details."])
                         [:form {:method "post" :action (str "/games/" (:game-token game) "/setup")}
                          (when anti-forgery-token
                            [:input {:type "hidden" :name "__anti-forgery-token" :value anti-forgery-token}])
                          [:fieldset
                           [:legend "Interior"]
                           [:label {:for "interior"} "Counter mood"
                            (select-input "interior" (or (:interior form) (:interior viewer)) (:interiors options) nil)]
                           (when-let [interior-errors (seq (:interior errors))]
                             [:ul.errors (for [error interior-errors] [:li error])])]
                          (map-indexed (fn [index dish] (dish-field options (inc index) dish errors)) dishes)
                          (layout/button "Schedule next tick")]])
          (layout/panel "panel--quiet"
                        [[:h2 "Pinboard"]
                         [:p.invite "Invite link · another fryer wanted: "
                          [:a {:href (str "/join/" (:invite-token game))}
                           (str "/join/" (:invite-token game))]]])
          [:script (hiccup/raw-string cost-script)]])
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (page {:game {:id 1 :name "Lunch rush" :tick 0} :viewer {:id 1 :name "Ada"}})
  )
