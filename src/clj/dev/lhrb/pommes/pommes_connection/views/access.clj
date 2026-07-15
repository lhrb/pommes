(ns dev.lhrb.pommes.pommes-connection.views.access
  (:require
   [dev.lhrb.pommes.pommes-connection.views.layout :as layout]
   [ring.util.response :as response]))

(defn- anti-forgery-input [token]
  (when token
    [:input {:type "hidden" :name "__anti-forgery-token" :value token}]))

(defn- error-list [messages]
  (when (seq messages)
    [:ul.errors (for [message messages] [:li message])]))

(defn- player-fields [form]
  [[:label {:for "player-name"} "Player name"
    [:input {:id "player-name" :name "player-name" :required true
             :autocomplete "username" :value (or (:player-name form) "")}]]
   [:label {:for "player-password"} "Password"
    [:input {:id "player-password" :name "player-password" :type "password" :required true
             :autocomplete "new-password"}]]])

(defn create-page [{:keys [form errors anti-forgery-token]}]
  (-> (layout/page
       "Create game | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Pommes Connection · city desk"]
         [:h1 "Open for business"]
         [:p.subhead "A new counter, a new rumor, a fresh batch."]]
        (layout/panel "panel--yellow"
                      [[:h2 "Start a game"]
                       [:p "Name the neighborhood affair. You run the first stand."]
                       (error-list errors)
                       (into [:form {:method "post" :action "/create-game"}
                              (anti-forgery-input anti-forgery-token)
                              [:label {:for "game-name"} "Game name"
                               [:input {:id "game-name" :name "game-name" :required true
                                        :value (or (:game-name form) "")}]]]
                             (concat (player-fields form) [(layout/button "Open the counter")]))])
        (layout/panel "panel--quiet"
                      [[:p "Already on the street?"]
                       [:a {:href "/login"} "Use your game code →"]])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn join-page [{:keys [game form errors anti-forgery-token]}]
  (-> (layout/page
       "Join game | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Extra! Counter vacancy"]
         [:h1 "Join the block"]
         [:p.subhead (str (:name game) " needs another fryer.")]]
        (layout/panel "panel--yellow"
                      [[:h2 "Claim your counter"]
                       (error-list errors)
                       (into [:form {:method "post" :action (str "/join/" (:invite-token game))}
                              (anti-forgery-input anti-forgery-token)]
                             (concat (player-fields form) [(layout/button "Join this game")]))])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn login-page [{:keys [game form errors anti-forgery-token]}]
  (-> (layout/page
       "Log in | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Pommes Connection · night edition"]
         [:h1 "Back at the counter"]
         [:p.subhead (str (:name game) " is still serving.")]]
        (layout/panel nil
                      [[:h2 "Log in"]
                       (error-list errors)
                       (into [:form {:method "post" :action (str "/login/" (:game-token game))}
                              (anti-forgery-input anti-forgery-token)]
                             (concat (player-fields form) [(layout/button "Return to the street")]))])
        (layout/panel "panel--quiet"
                      [[:p [:a {:href "/create-game"} "Start a new neighborhood →"]]])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn game-token-page []
  (-> (layout/page
       "Log in | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Street directory"]
         [:h1 "Find your game"]
         [:p.subhead "The code is on the invite, probably under a ketchup stain."]]
        (layout/panel nil
                      [[:form {:method "get" :action "/login"}
                        [:label {:for "game-token"} "Game code"
                         [:input {:id "game-token" :name "game-token" :required true
                                  :autocapitalize "characters"}]]
                        (layout/button "Find the counter")]])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(comment
  (create-page {}))
