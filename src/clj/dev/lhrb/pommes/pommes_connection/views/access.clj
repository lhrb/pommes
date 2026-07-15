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
        [:h1 "Create a game"]
        (error-list errors)
        (into [:form {:method "post" :action "/create-game"}
               (anti-forgery-input anti-forgery-token)
               [:label {:for "game-name"} "Game name"
                [:input {:id "game-name" :name "game-name" :required true
                         :value (or (:game-name form) "")}]]]
              (concat (player-fields form) [[:button {:type "submit"} "Create game"]]))
        [:p [:a {:href "/login"} "Log in to a game"]]])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn join-page [{:keys [game form errors anti-forgery-token]}]
  (-> (layout/page
       "Join game | Pommes Connection"
       [:main
        [:h1 "Join game"]
        [:p (str "Join " (:name game) ".")]
        (error-list errors)
        (into [:form {:method "post" :action (str "/join/" (:invite-token game))}
               (anti-forgery-input anti-forgery-token)]
              (concat (player-fields form) [[:button {:type "submit"} "Join game"]]))])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn login-page [{:keys [game form errors anti-forgery-token]}]
  (-> (layout/page
       "Log in | Pommes Connection"
       [:main
        [:h1 "Log in"]
        [:p (str "Log in to " (:name game) ".")]
        (error-list errors)
        (into [:form {:method "post" :action (str "/login/" (:game-token game))}
               (anti-forgery-input anti-forgery-token)]
              (concat (player-fields form) [[:button {:type "submit"} "Log in"]]))
        [:p [:a {:href "/create-game"} "Create a new game"]]])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn game-token-page []
  (-> (layout/page
       "Log in | Pommes Connection"
       [:main
        [:h1 "Log in"]
        [:form {:method "get" :action "/login"}
         [:label {:for "game-token"} "Game code"
          [:input {:id "game-token" :name "game-token" :required true
                   :autocapitalize "characters"}]]
         [:button {:type "submit"} "Continue"]]])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(comment
  (create-page {}))
