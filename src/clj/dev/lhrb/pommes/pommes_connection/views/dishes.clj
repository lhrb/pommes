(ns dev.lhrb.pommes.pommes-connection.views.dishes
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.game-data :as game-data]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
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

(def demographic-icons
  {:students "dem_student"
   :families "dem_families"
   :yuppies "dem_yuppi"
   :office-workers "dem_worker"
   :retirees "dem_retiree"
   :foodies "dem_foodie"})

(defn- demographic-icon [id alt]
  [:img.demographic-icon {:src (str "/assets/icons/" (get demographic-icons id "dem_foodie") "_48.png")
                          :alt alt :width 48 :height 48}])

(defn impact-panel [forecast]
  (let [changes (filter #(not (zero? (double (:share-change %)))) forecast)]
    [:section.market-impact {:aria-live "polite"}
     [:h3 "Auswirkung auf den nächsten Ansturm"]
     (if (seq changes)
       [:div.market-impact__changes
        (for [{:keys [demographic-id share-change]} changes]
          [:div.market-impact__change
           [:img {:src (str "/assets/icons/" (if (pos? share-change) "arrow_up" "arrow_down") "_48.png")
                  :alt (if (pos? share-change) "Steigt" "Sinkt") :width 24 :height 24}]
           (demographic-icon demographic-id "")
           [:span (label demographic-id)]])]
       [:p.meta "Die Kundschaft sieht vorerst keinen Grund, ihre Gewohnheiten zu ändern."])]))

(def player-colors ["#f5464c" "#4995f3" "#3ec54b" "#f09548" "#b22741" "#72deeb" "#b4e656" "#3c2f52"])

(defn- donut-slices [entries]
  (let [total (reduce + 0 (map :sales entries))]
    (when (pos? total)
      (second
       (reduce (fn [[offset slices] entry]
                 (let [size (* 100.0 (/ (double (:sales entry)) total))]
                   [(+ offset size) (conj slices (assoc entry :offset offset :size size))]))
               [0.0 []]
               entries)))))

(defn- donut [demographic-id entries]
  (let [slices (donut-slices entries)
        total (reduce + 0 (map :sales entries))]
    [:div.market-donut
     [:svg {:viewBox "0 0 120 120" :role "img"
            :aria-label (str (label demographic-id) ": " (quantity total) " Entscheidungen")}
      [:title (str "Marktanteile: " (label demographic-id))]
      [:circle.market-donut__track {:cx 60 :cy 60 :r 48}]
      (for [{:keys [id color offset size]} slices]
        [:circle.market-donut__slice {:cx 60 :cy 60 :r 48 :pathLength 100
                                      :stroke color
                                      :stroke-dasharray (str size " " (- 100.0 size))
                                      :stroke-dashoffset (- offset)}])
      [:circle.market-donut__center {:cx 60 :cy 60 :r 30}]]
     [:div.market-donut__icon (demographic-icon demographic-id "")]]))

(defn- latest-market-panel [{:keys [city players latest-tick]}]
  (when latest-tick
    (let [by-player (into {} (map (juxt :player-id identity) (:players latest-tick)))
          colors (zipmap (map :id players) (cycle player-colors))]
      [:section.panel.panel--tight.market-history {:x-data "{expanded:false}"}
       [:button.market-history__toggle {:type "button" "@click" "expanded = !expanded"
                                        ":aria-expanded" "expanded.toString()" :aria-controls "latest-market-details"}
        [:span (str "Marktanteile im letzten Tick · " (:tick latest-tick))]
        [:span {:aria-hidden "true" "x-text" "expanded ? '−' : '+'"} "+"]]
       [:div {:id "latest-market-details" :x-show "expanded" :x-cloak true}
        [:p.meta "Die Kundschaft verteilt sich. Manche gehen ohne Pommes nach Hause."]
        [:div.market-donut-grid
         (for [{:keys [id]} (:demographics city)
               :let [entries (concat
                              (for [player players]
                                {:id (:id player) :label (:name player)
                                 :sales (get-in by-player [(:id player) :demographic-sales id] 0)
                                 :color (get colors (:id player))})
                              [{:id :no-purchase :label "Kauft nicht"
                                :sales (get-in latest-tick [:demographic-unserved-demand id] 0)
                                :color "#8c7f90"}])]]
           [:article.market-donut-card
            [:h3 (label id)]
            (donut id entries)
            [:ul.market-donut__legend
             (for [{:keys [id label sales color]} entries]
               [:li {:key id}
                [:span.market-donut__swatch {:style (str "background:" color)}]
                [:span label] [:strong (quantity sales)]])]])]]])))

(defn page [{:keys [game viewer players city latest-tick form errors notice anti-forgery-token forecast hx?]}]
  (let [options (setup-options)
        dishes (take (:dish-count options) (concat (or (:dishes form) (:dishes viewer)) (repeat {})))]
    (-> (layout/game-response
         (str "Gerichte & Markt | " (:name game)) game viewer :dishes
         [[:header.screen-head (layout/icon "nav_dishes" "") [:div [:h1 "Gerichte & Markt"] [:p "Die Karte entscheidet, wer stehen bleibt."]]]
          (when notice (layout/notice "good" notice))
          (latest-market-panel {:city city :players players :latest-tick latest-tick})
          [:section {:id "menu-editor" :class "panel panel--yellow"}
                         [:h2 "Morgenkarte drucken"]
                         [:p "Änderungen gelten ab dem nächsten Ansturm. Die Kundschaft wird es sich merken, auf ihre Art."]
                         (when (seq errors) [:p.errors "Bitte korrigiere die markierten Menüdetails."])
                         [:form {:id "menu-form" :method "post"
                                 :action (str "/games/" (:game-token game) "/dishes/setup")
                                 :hx-post (str "/games/" (:game-token game) "/dishes/impact")
                                 :hx-trigger "input changed delay:350ms, change"
                                 :hx-target "#market-impact" :hx-swap "innerHTML"
                                 :x-data "{tab:1}"}
                          (when anti-forgery-token [:input {:type "hidden" :name "__anti-forgery-token" :value anti-forgery-token}])
                          [:fieldset [:legend "Innenleben"]
                           [:label {:for "interior"} "Budenstimmung"
                           (select-input "interior" (or (:interior form) (:interior viewer)) (:interiors options) nil)]
                           (when-let [interior-errors (seq (:interior errors))]
                             [:ul.errors (for [error interior-errors] [:li error])])]
                          [:div.menu-tabs {:role "tablist" :aria-label "Gericht auswählen"}
                           (for [index (range 1 (inc (:dish-count options)))]
                             [:button {:type "button" :role "tab" "@click" (str "tab=" index)
                                       ":aria-selected" (str "(tab === " index ").toString()")
                                       :aria-controls (str "dish-panel-" index)
                                       ":class" (str "{'is-active': tab === " index "}")}
                              (str "Gericht " index)])]
                          (map-indexed (fn [index dish]
                                         [:div.menu-tab__panel {:id (str "dish-panel-" (inc index))
                                                                :role "tabpanel" :x-cloak true
                                                                :x-show (str "tab === " (inc index))}
                                          (dish-field options (inc index) dish errors)])
                                       dishes)
                          [:div {:id "market-impact"} (impact-panel forecast)]
                          (layout/button {:type "button" :hx-post (str "/games/" (:game-token game) "/dishes/setup")
                                          :hx-include "#menu-form" :hx-target "#game-screen" :hx-swap "outerHTML"}
                                         "Karte speichern")]]]
         hx?)
        response/response
        (response/content-type "text/html; charset=utf-8"))))

(comment
  ;; (impact-panel [])
  )
