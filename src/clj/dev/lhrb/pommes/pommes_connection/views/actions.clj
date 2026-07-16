(ns dev.lhrb.pommes.pommes-connection.views.actions
  (:require
   [clojure.string :as str]
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn- label [value]
  ({:students "Studierende" :families "Familien" :retirees "Rentnerinnen und Rentner"
    :office-workers "Büromenschen" :yuppies "Yuppies" :foodies "Feinschmeckerinnen und Feinschmecker"}
   value (-> value name (str/replace #"-" " ") str/capitalize)))

(defn- percent [modifier]
  (str (format "%.0f" (* 100.0 (Math/abs (double modifier)))) "%"))

(defn- effect-label [{:keys [type demographic modifier]}]
  (case type
    :demographic-attraction (str (if (neg? modifier) "Weniger " "Mehr ") (label demographic))
    :demographic-repulsion (str "Weniger " (label demographic))
    :global-attraction (str (percent modifier) (if (neg? modifier)
                                                   " weniger Anziehung für alle"
                                                   " mehr Anziehung für alle"))
    :average-order-size (str (percent modifier) (if (neg? modifier)
                                                    " kleinere Bestellungen"
                                                    " größere Bestellungen"))
    :price-tolerance (str (percent modifier) (if (neg? modifier)
                                                 " weniger Preistoleranz"
                                                 " mehr Preistoleranz"))
    (name type)))

(defn- target-options [action viewer players]
  (case (:target action)
    :other-player (remove #(= (:id %) (:id viewer)) players)
    :any-player players
    []))

(defn- action-card [game viewer players anti-forgery-token action]
  (let [targets (target-options action viewer players)]
    (layout/panel
     "panel--yellow"
     [[:h2 (:name action)]
      [:p (str/join " · " (map effect-label (:effects action)))]
      [:p.meta (str "Wirkt " (* 30 (:duration action)) " Sekunden · "
                    "Abklingzeit " (* 30 (:cooldown action)) " Sekunden")]
      [:p [:strong (money (:cost action))] " · "
       (if (:available? action) "bereit" (str "wieder ab Tick " (:available-at-tick action)))]
      [:form {:method "post" :action (str "/games/" (:game-token game) "/actions")}
       [:input {:type "hidden" :name "__anti-forgery-token" :value anti-forgery-token}]
       [:input {:type "hidden" :name "action-id" :value (name (:id action))}]
       [:label {:for (str "target-" (name (:id action)))} "Ziel"
        [:select {:id (str "target-" (name (:id action))) :name "target-player-id"}
         (for [target targets]
           [:option {:value (:id target)} (:name target)])]]
       [:button {:type "submit" :disabled (or (not (:available? action)) (empty? targets))}
        (if (:available? action) "Aktion starten" "Noch nicht bereit")]]])))

(defn page
  [{:keys [game viewer players actions anti-forgery-token notice]}]
  (let [body (layout/page
              (str (:name game) " | Aktionen")
              [:main
               [:header.masthead
                [:p.eyebrow "Pommes Connection · Hinterzimmer"]
                [:h1 "Aktionen"]
                [:p.subhead (:name viewer) " · Kasse: " (money (:money viewer))]
                [:p.meta [:a {:href (str "/games/" (:game-token game))} "← Zur Budenübersicht"]]]
               (when notice (layout/notice (first notice) (second notice)))
               (map #(action-card game viewer players anti-forgery-token %) actions)])]
    (response/content-type (response/response body) "text/html; charset=utf-8")))

(comment
  ;; (page {:game {:name "Lunch" :game-token "ABC123"} :viewer {:name "Ada" :money 20}})
  )
