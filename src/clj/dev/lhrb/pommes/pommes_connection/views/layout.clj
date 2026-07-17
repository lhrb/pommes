(ns dev.lhrb.pommes.pommes-connection.views.layout
  (:require
   [clojure.string :as str]
   [hiccup2.core :as h]))

(defn panel
  ([content] (panel nil content))
  ([class content]
   (into [:section {:class (str "panel" (when class (str " " class)))}] content)))

(defn button
  ([label] (button {} label))
  ([attrs label]
   [:button (merge {:class "button" :type "submit" :hx-disabled-elt "this"} attrs) label]))

(defn badge [label] [:span.badge label])

(defn notice [kind content]
  [:div {:class (str "notice notice--" kind) :role (if (= kind "error") "alert" "status")}
   content])

(defn offer-card [content] (into [:article.offer-card] content))
(defn modal [content] (into [:div {:class "modal" :role "alert"}] content))
(defn stat-row [label value] [:div.stat-row [:span label] [:strong value]])
(defn meter [value]
  [:div.meter {:aria-hidden "true"}
   [:span {:style (str "width:" (max 8 (min 100 (long value))) "%")}]] )

(defn icon [name alt]
  [:img.icon {:src (str "/assets/icons/" name "_96.png") :alt alt :width 48 :height 48}])

(defn- money [value]
  (str "€" (str/replace (format "%.2f" (double (or value 0.0))) "." ",")))

(defn resource-bar [viewer oob?]
  [:section (cond-> {:id "resource-bar" :class "resource-bar" :aria-label "Deine Werte"}
              oob? (assoc :hx-swap-oob "true"))
   [:div.resource [:img.icon {:src "/assets/icons/res_money_48.png" :alt "" :width 32 :height 32}]
    [:span "Kasse"] [:strong {:data-flash-value true} (money (:money viewer))]]
   [:div.resource [:img.icon {:src "/assets/icons/res_pommes_48.png" :alt "" :width 32 :height 32}]
    [:span "Portionen"] [:strong {:data-flash-value true}
     (long (reduce + 0 (vals (get-in viewer [:game-stats :demographic-sales] {}))))]]
   [:div.resource [:img.icon {:src "/assets/icons/res_prestige_48.png" :alt "" :width 32 :height 32}]
    [:span "Prestige"] [:strong {:data-flash-value true} (long (or (:prestige viewer) 0))]]])

(def primary-nav
  [{:id :game :label "Bude" :icon "nav_game" :path ""}
   {:id :dishes :label "Karte" :icon "nav_dishes" :path "/dishes"}
   {:id :actions :label "Aktionen" :icon "nav_action" :path "/actions"}
   {:id :research :label "Forschung" :icon "nav_research" :path "/research"}
   {:id :black-market :label "Schwarz" :icon "nav_black" :path "/black-market"}])

(defn- nav-link [game active {:keys [id label icon path]}]
  [:a {:class (str "game-nav__item" (when (= id active) " is-active"))
       :href (str "/games/" (:game-token game) path)
       :hx-get (str "/games/" (:game-token game) path)
       :hx-target "#game-screen" :hx-swap "outerHTML" :hx-push-url "true"}
   [:img {:src (str "/assets/icons/" icon "_48.png") :alt "" :width 32 :height 32}]
   [:span label]])

(defn game-nav
  ([game active] (game-nav game active false))
  ([game active oob?]
   [:nav.game-nav (cond-> {:id "game-nav" :aria-label "Spielnavigation" :x-data "{more:false}"}
                    oob? (assoc :hx-swap-oob "true"))
    (map #(nav-link game active %) primary-nav)
    [:button.game-nav__item {:type "button" "@click" "more = !more" :aria-label "Weitere Bereiche"}
     [:img {:src "/assets/icons/nav_stats_48.png" :alt "" :width 32 :height 32}]
     [:span "Mehr"]]
    [:div.more-drawer {:x-show "more" :x-cloak true "@click.outside" "more=false"}
     (for [{:keys [label icon path]} [{:label "Stadtwerte" :icon "nav_stats" :path "/stats"}
                                     {:label "Chronik" :icon "nav_chronic" :path "/chronicle"}
                                     {:label "Budenwissen" :icon "nav_wiki" :path "/wiki"}]]
       [:a {:href (str "/games/" (:game-token game) path)
            :hx-get (str "/games/" (:game-token game) path)
            :hx-target "#game-screen" :hx-swap "outerHTML" :hx-push-url "true" "@click" "more=false"}
        [:img {:src (str "/assets/icons/" icon "_48.png") :alt "" :width 32 :height 32}] label])]]))

(defn screen [active content]
  (into [:main {:id "game-screen" :class (str "game-screen screen--" (name active))}] content))

(defn toast [kind message]
  (when message
    [:div {:id "toast-region" :class "toast-region" :aria-live "polite" :aria-atomic "true" :hx-swap-oob "true"}
     [:div {:class (str "toast toast--" kind)} message]]))

(defn game-hud [game server-now]
  [:div {:id "game-hud"
         :data-game-clock true
         :data-next-tick-at (:next-tick-at game)
         :data-server-now server-now
         :x-data (str "gameClock(" server-now "," (:next-tick-at game) ")")
         :x-init "start()"
         :hx-get (str "/games/" (:game-token game) "/hud")
         :hx-trigger "pommes:tick"
         :hx-target "this"
         :hx-swap "outerHTML"}
   [:div.game-header__title
    [:span "Pommes Connection · Tick " (:tick game) " · "
     [:span {:x-text "seconds + 's'"} "…"]]
    [:strong (:name game)]]])

(defn game-shell [title game viewer active content server-now]
  [:div.game-shell
   [:header.game-header
    [:img.game-header__image {:src "/assets/img/header.png" :alt "Pommesbude bei Nacht"}]
    (game-hud game server-now)]
   (resource-bar viewer false)
   (screen active content)
   [:div {:id "toast-region" :class "toast-region" :aria-live "polite" :aria-atomic "true"}]
   (game-nav game active)
   [:script "window.gameClock=function(serverNow,nextTickAt){return{seconds:0,timer:null,offset:serverNow-Date.now(),nextTickAt:nextTickAt,start:function(){var self=this,update=function(){self.seconds=Math.max(0,Math.ceil((self.nextTickAt-(Date.now()+self.offset))/1000));self.timer=window.setTimeout(function(){if(self.seconds===0){self.$dispatch(`pommes:tick`);}else{update();}},self.seconds===0?1000:500);};update();},destroy:function(){window.clearTimeout(this.timer);}}}"]])

(defn page [title content]
  (str (h/html
        [:html {:lang "de"}
         [:head [:meta {:charset "utf-8"}] [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
          [:link {:rel "preconnect" :href "https://fonts.googleapis.com"}]
          [:link {:rel "preconnect" :href "https://fonts.gstatic.com" :crossorigin true}]
          [:link {:rel "stylesheet" :href "https://fonts.googleapis.com/css2?family=Pixelify+Sans:wght@400;600;700&display=swap"}]
          [:link {:rel "stylesheet" :href "/assets/css/app.css"}]
          [:script {:defer true :src "https://unpkg.com/htmx.org@2.0.4"}]
          [:script {:defer true :src "https://unpkg.com/alpinejs@3.14.8/dist/cdn.min.js"}]
          [:script {:defer true :src "/assets/js/dishes.js"}]
          [:title title]]
         [:body content]])))

(defn game-response
  ([title game viewer active content hx?]
   (game-response title game viewer active content hx? (System/currentTimeMillis)))
  ([title game viewer active content hx? server-now]
   (if hx?
     (str (h/html (screen active content) (resource-bar viewer true) (game-nav game active true)))
     (page title (game-shell title game viewer active content server-now)))))

(defn game-hud-response [game viewer server-now]
  (str (h/html (game-hud game server-now) (resource-bar viewer true))))

(defn mutation-response [card viewer kind message]
  (str (h/html card (resource-bar viewer true) (toast kind message))))

(comment
  (page "Pommes Connection" [:main [:h1 "Hello"]]))
