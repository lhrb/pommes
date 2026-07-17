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

(defn game-nav [game active]
  [:nav.game-nav {:aria-label "Spielnavigation" :x-data "{more:false}"}
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
       [:img {:src (str "/assets/icons/" icon "_48.png") :alt "" :width 32 :height 32}] label])]])

(defn screen [active content]
  (into [:main {:id "game-screen" :class (str "game-screen screen--" (name active))}] content))

(defn toast [kind message]
  (when message
    [:div {:id "toast-region" :class "toast-region" :aria-live "polite" :aria-atomic "true" :hx-swap-oob "true"}
     [:div {:class (str "toast toast--" kind)} message]]))

(defn game-shell [title game viewer active content]
  [:div.game-shell
   [:header.game-header
    [:img.game-header__image {:src "/assets/img/header.png" :alt "Pommesbude bei Nacht"}]
    [:div.game-header__title [:span "Pommes Connection · Tick " (:tick game) " · " [:span {:id "shell-countdown"} "…"]] [:strong (:name game)]]
    [:div {:data-game-clock true :data-next-tick-at (:next-tick-at game)
           :hx-get (str "/games/" (:game-token game)) :hx-trigger "pommes:tick from:body"
           :hx-target "#game-screen" :hx-swap "outerHTML"}]]
   (resource-bar viewer false)
   (screen active content)
   [:div {:id "toast-region" :class "toast-region" :aria-live "polite" :aria-atomic "true"}]
   (game-nav game active)
   [:script "(function(){var c=document.querySelector('[data-game-clock]');if(!c)return;var end=Number(c.dataset.nextTickAt),out=document.getElementById('shell-countdown'),sent=false;setInterval(function(){var s=Math.max(0,Math.ceil((end-Date.now())/1000));if(out)out.textContent=s+'s';if(!sent&&s===0){sent=true;document.body.dispatchEvent(new Event('pommes:tick'));}},500)})();"]])

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
          [:title title]]
         [:body content]])))

(defn game-response [title game viewer active content hx?]
  (if hx?
    (str (h/html (screen active content) (resource-bar viewer true)))
    (page title (game-shell title game viewer active content))))

(defn mutation-response [card viewer kind message]
  (str (h/html card (resource-bar viewer true) (toast kind message))))

(comment
  (page "Pommes Connection" [:main [:h1 "Hello"]]))
