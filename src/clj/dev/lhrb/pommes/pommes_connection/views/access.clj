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
  [[:label {:for "player-name"} "Dein Budenname"
    [:input {:id "player-name" :name "player-name" :required true
             :autocomplete "username" :value (or (:player-name form) "")}]]
   [:label {:for "player-password"} "Passwort"
    [:input {:id "player-password" :name "player-password" :type "password" :required true
             :autocomplete "new-password"}]]])

(defn create-page [{:keys [form errors anti-forgery-token]}]
  (-> (layout/page
       "Spiel eröffnen | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Pommes Connection · die Lokalredaktion"]
         [:h1 "Die Fritteuse ist eröffnet"]
         [:p.subhead "Eine neue Bude, ein neues Gerücht, eine frische Ladung."]]
        (layout/panel "panel--yellow"
                      [[:h2 "Spiel starten"]
                       [:p "Gib der Nachbarschaftssause einen Namen. Du schmeißt die erste Bude."]
                       (error-list errors)
                       (into [:form {:method "post" :action "/create-game"}
                              (anti-forgery-input anti-forgery-token)
                              [:label {:for "game-name"} "Name des Spiels"
                               [:input {:id "game-name" :name "game-name" :required true
                                        :value (or (:game-name form) "")}]]]
                             (concat (player-fields form) [(layout/button "Bude eröffnen")]))])
        (layout/panel "panel--quiet"
                      [[:p "Schon auf der Straße unterwegs?"]
                       [:a {:href "/login"} "Mit Spielcode reinspazieren →"]])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn join-page [{:keys [game form errors anti-forgery-token]}]
  (-> (layout/page
       "Spiel beitreten | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Extrablatt! Bude frei"]
         [:h1 "Komm in die Nachbarschaft"]
         [:p.subhead (str (:name game) " braucht noch eine Fritteuse.")]]
        (layout/panel "panel--yellow"
                      [[:h2 "Schnapp dir deine Bude"]
                       (error-list errors)
                       (into [:form {:method "post" :action (str "/join/" (:invite-token game))}
                              (anti-forgery-input anti-forgery-token)]
                             (concat (player-fields form) [(layout/button "Diesem Spiel beitreten")]))])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn login-page [{:keys [game form errors anti-forgery-token]}]
  (-> (layout/page
       "Anmelden | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Pommes Connection · Nachtausgabe"]
         [:h1 "Zurück an die Bude"]
         [:p.subhead (str (:name game) " hat noch geöffnet.")]]
        (layout/panel nil
                      [[:h2 "Anmelden"]
                       (error-list errors)
                       (into [:form {:method "post" :action (str "/login/" (:game-token game))}
                              (anti-forgery-input anti-forgery-token)]
                             (concat (player-fields form) [(layout/button "Zurück auf die Straße")]))])
        (layout/panel "panel--quiet"
                      [[:p [:a {:href "/create-game"} "Neue Nachbarschaft eröffnen →"]]])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(defn game-token-page []
  (-> (layout/page
       "Anmelden | Pommes Connection"
       [:main
        [:header.masthead
         [:p.eyebrow "Straßenverzeichnis"]
         [:h1 "Finde dein Spiel"]
         [:p.subhead "Der Code steht auf der Einladung, vermutlich unter einem Klecks Ketchup."]]
        (layout/panel nil
                      [[:form {:method "get" :action "/login"}
                        [:label {:for "game-token"} "Spielcode"
                         [:input {:id "game-token" :name "game-token" :required true
                                  :autocapitalize "characters"}]]
                        (layout/button "Bude finden")]])])
      response/response
      (response/content-type "text/html; charset=utf-8")))

(comment
  (create-page {}))
