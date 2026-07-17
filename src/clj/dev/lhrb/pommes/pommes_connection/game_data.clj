(ns dev.lhrb.pommes.pommes-connection.game-data)

(def game-data
  {:rules
   {:tick-seconds 30
    :choice-beta 3.5
    :market-memory-alpha 0.10
    :relative-price-cap 0.25
    :status-cap 0.25
    :budget-price-ratio 0.80
    :premium-price-ratio 1.40
    :positioning-categories [:budget :mainstream :premium]}

   :regulars
   {:base-conversion-chance 0.03
    :loss-rate 0.001
    :maximum-population-share 0.20}

   :research
   {:cost {:base 20.0 :multiplier 2.0 :maximum-level 25}
    :items
    {:potato-quality {:id :potato-quality :name "Kartoffelqualität"
                      :description "Der Kartoffelhändler verspricht eine festere Sorte. Er hat dafür einen Stempel."
                      :effect :conversion :maximum-modifier 0.30}
     :fryer {:id :fryer :name "Fritteuse"
              :description "Die Fritteuse hält die Temperatur. Zumindest, wenn man sie nicht dabei beobachtet."
              :effect :conversion :maximum-modifier 0.30}
     :fry-grease {:id :fry-grease :name "Frittierfett"
                   :description "Das Fett wird regelmäßig gewechselt. Der Schichtleiter führt darüber ein Heft."
                   :effect :reliability :maximum-modifier 0.30}
     :ketchup-recipe {:id :ketchup-recipe :name "Ketchup-Rezept"
                      :description "Das Rezept ist geheim. Drei Leute kennen es, und keiner darf gleichzeitig Urlaub nehmen."
                      :effect :order-value :toppings #{:ketchup :ketchup-mayo :curry-ketchup}
                      :maximum-modifier 0.30}
     :mayo-recipe {:id :mayo-recipe :name "Mayo-Rezept"
                   :description "Die Mayo wird nach Vorschrift gerührt. Die Vorschrift ist älter als die Bude."
                   :effect :order-value :toppings #{:mayo :ketchup-mayo :garlic-sauce}
                   :maximum-modifier 0.30}}}

   :black-market
   {:cost {:base 20.0 :multiplier 2.0 :maximum-level 25}
    :tax-rate 0.19
    :suspicion-gain-per-concealment 0.10
    :suspicion-decay 0.97
    :audit-every-ticks 10
    :base-audit-chance 0.05
    :maximum-audit-chance 0.55
    :audit-penalty-ticks 3
    :items
    {:creative-bookkeeping {:id :creative-bookkeeping :name "Kreative Buchführung"
                            :description "Einnahmen verschwinden zwischen den Zeilen."
                            :maximum-concealment 0.10}
     :cash-only-register {:id :cash-only-register :name "Nur-Bar-Kasse"
                           :description "Die Kasse kennt erstaunlich wenig Kartenumsatz."
                           :maximum-concealment 0.10}
     :uncle-rudis-accounting {:id :uncle-rudis-accounting :name "Onkel Rudis Buchhaltung"
                               :description "Onkel Rudi findet für alles eine Quittung."
                               :maximum-concealment 0.10}}}

   :catalog
   {:sizes {:small {:cost 0.55}
            :medium {:cost 0.70}
            :large {:cost 0.85}
            :extra-large {:cost 1.00}}
    :seasonings {:salt {:cost 0.03}
                 :pommesgewuerz {:cost 0.09}}
    :toppings {:none {:cost 0.00}
               :ketchup {:cost 0.12}
               :mayo {:cost 0.16}
               :ketchup-mayo {:cost 0.25}
               :special {:cost 0.55}
               :sate {:cost 0.60}
               :curry-ketchup {:cost 0.16}
               :garlic-sauce {:cost 0.20}}
    :interiors {:classic {}
                :modern {}
                :cozy {}
                :street-food {}
                :premium {}}}

   :setup
   {:dish-count 3
    :starter
    {:interior :classic
     :dishes [{:name "Kleine Pommes"
               :pommes {:size :small :seasoning :salt}
               :topping :ketchup-mayo
               :price 4.0}
              {:name "Große Pommes"
               :pommes {:size :large :seasoning :salt}
               :topping :ketchup-mayo
               :price 6.0}
              {:name "Pommes Special"
               :pommes {:size :large :seasoning :salt}
               :topping :special
               :price 7.0}]}}

   :actions
   {:send-punks
    {:id :send-punks
     :name "Die Punks kommen"
     :target :other-player
     :duration 12
     :cooldown 18
     :cost {:income-multiplier 2.0 :cash-ratio 0.0025}
     :effects [{:type :demographic-attraction :demographic :students :modifier 0.10}
               {:type :demographic-repulsion :demographic :families :modifier 0.20}
               {:type :demographic-repulsion :demographic :retirees :modifier 0.20}
               {:type :global-attraction :modifier -0.15}]}
    :rocker-meeting
    {:id :rocker-meeting
     :name "Motorräder vor der Tür"
     :target :other-player
     :duration 18
     :cooldown 18
     :cost {:income-multiplier 1.0 :cash-ratio 0.0015}
     :effects [{:type :demographic-attraction :demographic :office-workers :modifier -0.10}
               {:type :demographic-attraction :demographic :students :modifier 0.10}
               {:type :average-order-size :modifier 0.20}
               {:type :global-attraction :modifier -0.15}]}
    :influencer-visit
    {:id :influencer-visit
     :name "Besuch mit Ringlicht"
     :target :any-player
     :duration 9
     :cooldown 18
     :cost {:income-multiplier 0.75 :cash-ratio 0.001}
     :effects [{:type :demographic-attraction :demographic :yuppies :modifier 0.35}
               {:type :demographic-attraction :demographic :foodies :modifier 0.25}
               {:type :price-tolerance :modifier 0.15}]}
    :parking-chaos
    {:id :parking-chaos
     :name "Neue Parkplatzordnung"
     :target :other-player
     :duration 15
     :cooldown 21
     :cost {:income-multiplier 1.5 :cash-ratio 0.002}
     :effects [{:type :demographic-repulsion :demographic :families :modifier 0.25}
               {:type :demographic-repulsion :demographic :retirees :modifier 0.20}
               {:type :global-attraction :modifier -0.10}
               {:type :average-order-size :modifier -0.10}]}
    :review-bomb
    {:id :review-bomb
     :name "Erfahrungsberichte"
     :target :other-player
     :duration 9
     :cooldown 21
     :cost {:income-multiplier 1.25 :cash-ratio 0.00175}
     :effects [{:type :demographic-repulsion :demographic :yuppies :modifier 0.25}
               {:type :demographic-repulsion :demographic :foodies :modifier 0.25}
               {:type :global-attraction :modifier -0.10}
               {:type :price-tolerance :modifier -0.15}]}
    :neighborhood-festival
    {:id :neighborhood-festival
     :name "Das Nachbarschaftsfest"
     :target :any-player
     :duration 12
     :cooldown 18
     :cost {:income-multiplier 1.0 :cash-ratio 0.0015}
     :effects [{:type :global-attraction :modifier 0.10}
               {:type :demographic-attraction :demographic :families :modifier 0.15}
               {:type :average-order-size :modifier 0.10}
               {:type :price-tolerance :modifier 0.10}]}
    :cost {:minimum 20.0}}

   :market
   {:price-anchors {:students 4.50
                    :families 5.50
                    :yuppies 6.00
                    :office-workers 5.25
                    :retirees 4.75
                    :foodies 7.50}}

   :city
   {:initial-population 100
    :maximum-population 1000000
    :population-growth-half-life-ticks 1440
    :demographics-update-every-ticks 60
    ;; At tick 5,760 the growth curve has reached roughly 93.75% of capacity.
    ;; That is where the city starts writing its own demographic story.
    :composition-drift-start-population 937500
    :minimum-demographic-share 0.05
    :maximum-demographic-share 0.40
    :maximum-share-change 0.01

    :demographics
    [{:id :students
      :population 500
      :eat-rate 0.12
      :outside-option-utility 0.48
      :behavior {:topping-variety-strength 0.20
                 :positioning-variety-strength 0.10}
      :preferences
      {:weights {:size 1.0 :seasoning 0.3 :topping 0.9 :price 1.8 :interior 0.2}
       :size {:small 0.9 :medium 0.7 :large 0.5 :extra-large 0.3}
       :seasoning {:salt 0.8 :pommesgewuerz 0.7}
       :topping {:none 0.3 :ketchup 1.0 :mayo 0.7 :ketchup-mayo 0.9
                 :special 0.4 :sate 0.5 :curry-ketchup 0.8 :garlic-sauce 0.6}
       :price {:model :cheap :max 5.00 :anchor 4.50 :relative-price-strength 0.25}
       :interior {:classic 0.5 :modern 0.8 :cozy 0.6 :street-food 1.0 :premium 0.4}}}

     {:id :families
      :population 350
      :eat-rate 0.08
      :outside-option-utility 0.50
      :behavior {:topping-variety-strength 0.05
                 :positioning-variety-strength 0.03}
      :preferences
      {:weights {:size 1.3 :seasoning 0.4 :topping 0.7 :price 1.0 :interior 0.8}
       :size {:small 0.7 :medium 0.8 :large 0.9 :extra-large 1.0}
       :seasoning {:salt 1.0 :pommesgewuerz 0.6}
       :topping {:none 0.5 :ketchup 1.0 :mayo 0.8 :ketchup-mayo 0.9
                 :special 0.5 :sate 0.2 :curry-ketchup 0.6 :garlic-sauce 0.5}
       :price {:model :value :ideal 5.50 :tolerance 2.25 :anchor 5.50 :relative-price-strength 0.10}
       :interior {:classic 1.0 :modern 0.8 :cozy 0.9 :street-food 0.6 :premium 0.5}}}

     {:id :yuppies
      :population 180
      :eat-rate 0.07
      :outside-option-utility 0.52
      :behavior {:topping-variety-strength 0.35
                 :positioning-variety-strength 0.30}
      :preferences
      {:weights {:size 1.3 :seasoning 0.5 :topping 1.4 :price 1.0 :interior 1.3}
       :size {:small 1.0 :medium 0.65 :large 0.3 :extra-large 0.0}
       :seasoning {:salt 0.4 :pommesgewuerz 1.0}
       :topping {:none 0.2 :ketchup 0.1 :mayo 0.2 :ketchup-mayo 0.1
                 :special 1.0 :sate 0.8 :curry-ketchup 0.7 :garlic-sauce 0.7}
       :price {:model :status :ideal 9.00 :tolerance 3.00 :anchor 6.00 :status-strength 0.40}
       :interior {:classic 0.2 :modern 1.0 :cozy 0.6 :street-food 0.8 :premium 1.0}}}

     {:id :office-workers
      :population 300
      :eat-rate 0.11
      :outside-option-utility 0.50
      :behavior {:topping-variety-strength 0.10
                 :positioning-variety-strength 0.05}
      :preferences
      {:weights {:size 0.8 :seasoning 0.5 :topping 0.8 :price 1.5 :interior 0.6}
       :size {:small 0.6 :medium 1.0 :large 0.7 :extra-large 0.3}
       :seasoning {:salt 0.8 :pommesgewuerz 0.9}
       :topping {:none 0.2 :ketchup 0.8 :mayo 0.5 :ketchup-mayo 0.6
                 :special 0.5 :sate 0.5 :curry-ketchup 1.0 :garlic-sauce 0.7}
       :price {:model :value :ideal 5.25 :tolerance 1.75 :anchor 5.25
               :relative-price-strength 0.15}
       :interior {:classic 0.5 :modern 1.0 :cozy 0.6 :street-food 0.9 :premium 0.5}}}

     {:id :retirees
      :population 220
      :eat-rate 0.09
      :outside-option-utility 0.54
      :behavior {:topping-variety-strength 0.0
                 :positioning-variety-strength 0.0}
      :preferences
      {:weights {:size 0.8 :seasoning 0.4 :topping 0.6 :price 1.4 :interior 1.2}
       :size {:small 0.8 :medium 1.0 :large 0.6 :extra-large 0.2}
       :seasoning {:salt 1.0 :pommesgewuerz 0.4}
       :topping {:none 0.8 :ketchup 0.8 :mayo 0.9 :ketchup-mayo 0.7
                 :special 0.3 :sate 0.1 :curry-ketchup 0.3 :garlic-sauce 0.4}
       :price {:model :cheap :max 5.50 :anchor 4.75 :relative-price-strength 0.15}
       :interior {:classic 1.0 :modern 0.4 :cozy 1.0 :street-food 0.3 :premium 0.5}}}

     {:id :foodies
      :population 140
      :eat-rate 0.06
      :outside-option-utility 0.55
      :behavior {:topping-variety-strength 0.30
                 :positioning-variety-strength 0.25}
      :preferences
      {:weights {:size 0.7 :seasoning 1.2 :topping 1.5 :price 0.8 :interior 1.1}
       :size {:small 0.9 :medium 0.8 :large 0.5 :extra-large 0.2}
       :seasoning {:salt 0.3 :pommesgewuerz 1.0}
       :topping {:none 0.1 :ketchup 0.2 :mayo 0.3 :ketchup-mayo 0.2
                 :special 0.8 :sate 0.9 :curry-ketchup 0.7 :garlic-sauce 1.0}
       :price {:model :status :ideal 7.50 :tolerance 2.50 :anchor 7.50
               :status-strength 0.30}
       :interior {:classic 0.3 :modern 0.8 :cozy 0.8 :street-food 0.7 :premium 1.0}}}]}})

(defn simulation-config []
  (select-keys game-data [:rules :regulars :research :black-market :catalog :city :market]))

(defn starter-setup []
  (get-in game-data [:setup :starter]))

(defn actions []
  (dissoc (:actions game-data) :cost))

(defn action [action-id]
  (get (actions) action-id))

(defn research-items []
  (get-in game-data [:research :items]))

(defn research-item [research-id]
  (get (research-items) research-id))

(defn black-market-items []
  (get-in game-data [:black-market :items]))

(defn black-market-item [item-id]
  (get (black-market-items) item-id))

(comment
  ;; Add a topping, interior, or demographic by editing game-data.
  (keys (get-in game-data [:catalog :toppings])))
