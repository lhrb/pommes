(ns dev.lhrb.pommes.pommes-connection.views.game
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(def interiors [:classic :modern :punk])
(def sizes [:small :large])
(def seasonings [:salt :pommesgewuerz])
(def toppings [:none :ketchup :mayo :ketchup-mayo :special :sate])

(defn- label [value]
  (-> value name (str/replace #"-" " ") str/capitalize))

(defn- money [value]
  (format "€%.2f" (double (or value 0.0))))

(defn- quantity [value]
  (format "%.1f" (double (or value 0.0))))

(defn- select-input [field value options]
  [:select {:name field :id field}
   (for [option options]
     [:option {:value (name option) :selected (= option value)} (label option)])])

(defn- text-input [field value attributes]
  [:input (merge {:name field :id field :value (or value "")} attributes)])

(defn- dish-field [index dish errors]
  (let [prefix (str "dish-" index "-")
        pommes (:pommes dish)]
    [:fieldset
     [:legend (str "Dish " index)]
     [:div.grid
      [:label {:for (str prefix "name")} "Name"
       (text-input (str prefix "name") (:name dish) {:required true})]
      [:label {:for (str prefix "size")} "Size"
       (select-input (str prefix "size") (:size pommes) sizes)]
      [:label {:for (str prefix "portion")} "Portion"
       (text-input (str prefix "portion") (:portion pommes)
                   {:type "number" :step "0.1" :min "0.1" :required true})]
      [:label {:for (str prefix "seasoning")} "Seasoning"
       (select-input (str prefix "seasoning") (:seasoning pommes) seasonings)]
      [:label {:for (str prefix "topping")} "Topping"
       (select-input (str prefix "topping") (:topping dish) toppings)]
      [:label {:for (str prefix "price")} "Price"
       (text-input (str prefix "price") (:price dish)
                   {:type "number" :step "0.01" :min "0" :required true})]]
     (when-let [dish-errors (seq (get errors index))]
       [:ul.errors (for [error dish-errors] [:li error])])]))

(defn- viewer-tick-summary [latest-tick viewer-id]
  (some #(when (= viewer-id (:player-id %)) %) (:players latest-tick)))

(defn page
  [{:keys [game viewer players city latest-tick form errors scheduled-for]}]
  (let [viewer-result (viewer-tick-summary latest-tick (:id viewer))
        dishes (take 3 (concat (or (:dishes form) (:dishes viewer)) (repeat {})))]
    (-> (layout/page
         (str (:name game) " | Pommes Connection")
         [:main
          [:header
           [:h1 (:name game)]
           [:p (str "Playing as " (:name viewer) " · Tick " (:tick game))]]
          (when scheduled-for
            [:p.notice (str "Your setup is scheduled for tick " scheduled-for ".")])
          [:section
           [:h2 "Current demographics"]
           [:table
            [:thead [:tr [:th "Segment"] [:th "Population"] [:th "Potential demand / tick"]]]
            [:tbody
             (for [{:keys [id population eat-rate]} (:demographics city)]
               [:tr [:td (label id)] [:td population]
                [:td.numeric (quantity (* population eat-rate))]])]]]
          [:section
           [:h2 "Players"]
           [:table
            [:thead [:tr [:th "Player"] [:th "Money"] [:th "Prestige"]]]
            [:tbody
             (for [player players]
               [:tr [:td (:name player)] [:td.numeric (money (:money player))]
                [:td.numeric (quantity (:prestige player))]])]]]
          [:section
           [:h2 "Last tick"]
           (if viewer-result
             [:div
              [:p (str "Tick " (:tick latest-tick))]
              [:table
               [:thead [:tr [:th "Sales"] [:th "Revenue"] [:th "Profit"] [:th "Money"] [:th "Prestige"]]]
               [:tbody [:tr [:td.numeric (quantity (:sales viewer-result))]
                        [:td.numeric (money (:revenue viewer-result))]
                        [:td.numeric (money (:profit viewer-result))]
                        [:td.numeric (money (:money viewer-result))]
                        [:td.numeric (quantity (:prestige viewer-result))]]]]
              [:h3 "Sales by demographic"]
              [:table
               [:thead [:tr [:th "Segment"] [:th "Sales"]]]
               [:tbody
                (for [{:keys [id]} (:demographics city)]
                  [:tr [:td (label id)]
                   [:td.numeric (quantity (get (:demographic-sales viewer-result) id))]])]]]
             [:p "No tick has completed yet."])]
          [:section
           [:h2 "Set up your dishes"]
           [:p "Changes are applied at the next tick."]
           (when (seq errors)
             [:p.errors "Please correct the highlighted dish values."])
           [:form {:method "post" :action (str "/games/" (:id game) "/players/" (:id viewer) "/setup")}
            [:fieldset
             [:legend "Interior"]
             [:label {:for "interior"} "Style"
              (select-input "interior" (or (:interior form) (:interior viewer)) interiors)]
             (when-let [interior-errors (seq (:interior errors))]
               [:ul.errors (for [error interior-errors] [:li error])])]
            (map-indexed (fn [index dish] (dish-field (inc index) dish errors)) dishes)
            [:button {:type "submit"} "Schedule setup"]]]])
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (page {:game {:id 1 :name "Lunch rush" :tick 0} :viewer {:id 1 :name "Ada"}})
  )
