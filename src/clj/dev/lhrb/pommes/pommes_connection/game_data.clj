(ns dev.lhrb.pommes.pommes-connection.game-data)

(def game-data
  {:game
   {:player-count 3
    :duration-ticks 20160
    :score-every-ticks 720}

   :rules
   {:tick-seconds 30
    :default-order-rate-per-tick 0.012
    :choice-beta 3.5
    :market-memory-alpha 0.10
    :relative-price-cap 0.25
    :status-cap 0.25
    :budget-price-ratio 0.80
    :premium-price-ratio 1.40
    :positioning-categories [:budget :mainstream :premium]}

   :regulars
   {:base-conversion-chance 0.02
    :loss-rate 0.0002
    :maximum-population-share 0.20}

   :economy
   {:starting-money 6000000
    :reference-profit-per-tick {:opening 12000 :development 24000 :late 80000}}

   :prestige
   {:market-share-pool 2
    :best-dish-pool 2
    :ranking-points [3 1]
    :regulars-points [4 2]
    :research-branch-pool 1
    :black-market-branch-pool 1
    :audit-penalty 3}

   :research
   {:cost {:income-hours [3 6 12 21 36] :maximum-level 5}
    :items
    {:potato-quality {:id :potato-quality :name "Kartoffelqualität"
                      :description "Der Kartoffelhändler verspricht eine festere Sorte. Er hat dafür einen Stempel."
                      :effect :concept-quality :maximum-modifier 0.12}
     :fryer {:id :fryer :name "Fritteuse"
              :description "Die Fritteuse hält die Temperatur. Zumindest, wenn man sie nicht dabei beobachtet."
              :effect :conversion :maximum-modifier 0.30}
     :fry-grease {:id :fry-grease :name "Frittierfett"
                   :description "Das Fett wird regelmäßig gewechselt. Der Schichtleiter führt darüber ein Heft."
                   :effect :ingredient-efficiency :maximum-modifier 0.15}
     :ketchup-recipe {:id :ketchup-recipe :name "Ketchup-Rezept"
                      :description "Das Rezept ist geheim. Drei Leute kennen es, und keiner darf gleichzeitig Urlaub nehmen."
                      :effect :order-value :toppings #{:ketchup :ketchup-mayo :curry-ketchup}
                      :maximum-modifier 0.18}
     :mayo-recipe {:id :mayo-recipe :name "Mayo-Rezept"
                   :description "Die Mayo wird nach Vorschrift gerührt. Die Vorschrift ist älter als die Bude."
                   :effect :order-value :toppings #{:mayo :ketchup-mayo :garlic-sauce}
                   :maximum-modifier 0.18}}}

   :black-market
   {:cost {:income-hours [5 10 20 40 70] :maximum-level 5}
    :tax-rate 0.19
    :suspicion-gain-per-concealment 0.12
    :suspicion-decay 0.995
    :audit-every-ticks 720
    :base-audit-chance 0.04
    :maximum-audit-chance 0.45
    :audit-penalty-ticks 720
    :audit-profit-window-ticks 720
    :items
    {:creative-bookkeeping {:id :creative-bookkeeping :name "Kreative Buchführung"
                            :description "Einnahmen verschwinden zwischen den Zeilen."
                            :effect :concealment
                            :maximum-concealment 0.25}
     :cash-only-register {:id :cash-only-register :name "Nur-Bar-Kasse"
                           :description "Die Kasse kennt erstaunlich wenig Kartenumsatz."
                           :effect :untaxed-income
                           :maximum-untaxed-income 0.08}
     :uncle-rudis-accounting {:id :uncle-rudis-accounting :name "Onkel Rudis Buchhaltung"
                               :description "Onkel Rudi findet für alles eine Quittung."
                               :effect :audit-protection
                               :maximum-protection 0.50}}}

   :catalog
   {:sizes {:small {:cost 55}
            :medium {:cost 70}
            :large {:cost 85}
            :extra-large {:cost 100}}
    :seasonings {:salt {:cost 3}
                 :pommesgewuerz {:cost 9}}
    :toppings {:none {:cost 0}
               :ketchup {:cost 12}
               :mayo {:cost 16}
               :ketchup-mayo {:cost 25}
               :special {:cost 55}
               :sate {:cost 60}
               :curry-ketchup {:cost 16}
               :garlic-sauce {:cost 20}}
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
               :price 400}
              {:name "Große Pommes"
               :pommes {:size :large :seasoning :salt}
               :topping :ketchup-mayo
               :price 600}
              {:name "Pommes Special"
               :pommes {:size :large :seasoning :salt}
               :topping :special
               :price 700}]}}

   :actions
   {:maximum-uses 2
    :reset-ticks 1440
    :send-punks
    {:id :send-punks
     :name "Die Punks kommen"
     :target :other-player
     :duration 240
     :cost {:income-hours 0.5}
     :effects [{:type :demographic-attraction :demographic :students :modifier 0.10}
               {:type :demographic-repulsion :demographic :families :modifier 0.20}
               {:type :demographic-repulsion :demographic :retirees :modifier 0.20}
               {:type :global-attraction :modifier -0.15}]}
    :rocker-meeting
    {:id :rocker-meeting
     :name "Motorräder vor der Tür"
     :target :other-player
     :duration 300
     :cost {:income-hours 0.4}
     :effects [{:type :demographic-attraction :demographic :office-workers :modifier -0.10}
               {:type :demographic-attraction :demographic :students :modifier 0.10}
               {:type :average-order-size :modifier 0.20}
               {:type :global-attraction :modifier -0.15}]}
    :influencer-visit
    {:id :influencer-visit
     :name "Besuch mit Ringlicht"
     :target :any-player
     :duration 180
     :cost {:income-hours 0.35}
     :effects [{:type :demographic-attraction :demographic :yuppies :modifier 0.35}
               {:type :demographic-attraction :demographic :foodies :modifier 0.25}
               {:type :price-tolerance :modifier 0.15}]}
    :parking-chaos
    {:id :parking-chaos
     :name "Neue Parkplatzordnung"
     :target :other-player
     :duration 300
     :cost {:income-hours 0.5}
     :effects [{:type :demographic-repulsion :demographic :families :modifier 0.25}
               {:type :demographic-repulsion :demographic :retirees :modifier 0.20}
               {:type :global-attraction :modifier -0.10}
               {:type :average-order-size :modifier -0.10}]}
    :review-bomb
    {:id :review-bomb
     :name "Erfahrungsberichte"
     :target :other-player
     :duration 180
     :cost {:income-hours 0.4}
     :effects [{:type :demographic-repulsion :demographic :yuppies :modifier 0.25}
               {:type :demographic-repulsion :demographic :foodies :modifier 0.25}
               {:type :global-attraction :modifier -0.10}
               {:type :price-tolerance :modifier -0.15}]}
    :neighborhood-festival
    {:id :neighborhood-festival
     :name "Das Nachbarschaftsfest"
     :target :any-player
     :duration 240
     :cost {:income-hours 0.4}
     :effects [{:type :global-attraction :modifier 0.10}
               {:type :demographic-attraction :demographic :families :modifier 0.15}
               {:type :average-order-size :modifier 0.10}
               {:type :price-tolerance :modifier 0.10}]}
    :cost {:minimum 5000}}

   :market
   {:price-anchors {:students 450
                    :families 550
                    :yuppies 600
                    :office-workers 525
                    :retirees 475
                    :foodies 750}}

   :city
   {:population-anchors [[0 15000]
                         [2880 30000]
                         [11520 70000]
                         [20160 100000]]
    :demographics-update-every-ticks 720
    :composition-drift-start-tick 2880
    :minimum-demographic-share 0.07
    :maximum-demographic-share 0.35
    :maximum-share-change 0.015
    :trend-transfer-share [0.08 0.12]
    :trend-completion-tolerance 0.001

    :demographics
    [{:id :students
      :population 500
      :order-rate-per-tick 0.012
      :outside-option-utility 0.48
      :behavior {:topping-variety-strength 0.20
                 :positioning-variety-strength 0.10}
      :preferences
      {:weights {:size 1.0 :seasoning 0.3 :topping 0.9 :price 1.8 :interior 0.2}
       :size {:small 0.9 :medium 0.7 :large 0.5 :extra-large 0.3}
       :seasoning {:salt 0.8 :pommesgewuerz 0.7}
       :topping {:none 0.3 :ketchup 1.0 :mayo 0.7 :ketchup-mayo 0.9
                 :special 0.4 :sate 0.5 :curry-ketchup 0.8 :garlic-sauce 0.6}
       :price {:model :cheap :max 500 :anchor 450 :relative-price-strength 0.25}
       :interior {:classic 0.5 :modern 0.8 :cozy 0.6 :street-food 1.0 :premium 0.4}}}

     {:id :families
      :population 350
      :order-rate-per-tick 0.012
      :outside-option-utility 0.50
      :behavior {:topping-variety-strength 0.05
                 :positioning-variety-strength 0.03}
      :preferences
      {:weights {:size 1.3 :seasoning 0.4 :topping 0.7 :price 1.0 :interior 0.8}
       :size {:small 0.7 :medium 0.8 :large 0.9 :extra-large 1.0}
       :seasoning {:salt 1.0 :pommesgewuerz 0.6}
       :topping {:none 0.5 :ketchup 1.0 :mayo 0.8 :ketchup-mayo 0.9
                 :special 0.5 :sate 0.2 :curry-ketchup 0.6 :garlic-sauce 0.5}
       :price {:model :value :ideal 550 :tolerance 225 :anchor 550 :relative-price-strength 0.10}
       :interior {:classic 1.0 :modern 0.8 :cozy 0.9 :street-food 0.6 :premium 0.5}}}

     {:id :yuppies
      :population 180
      :order-rate-per-tick 0.012
      :outside-option-utility 0.52
      :behavior {:topping-variety-strength 0.35
                 :positioning-variety-strength 0.30}
      :preferences
      {:weights {:size 1.3 :seasoning 0.5 :topping 1.4 :price 1.0 :interior 1.3}
       :size {:small 1.0 :medium 0.65 :large 0.3 :extra-large 0.0}
       :seasoning {:salt 0.4 :pommesgewuerz 1.0}
       :topping {:none 0.2 :ketchup 0.1 :mayo 0.2 :ketchup-mayo 0.1
                 :special 1.0 :sate 0.8 :curry-ketchup 0.7 :garlic-sauce 0.7}
       :price {:model :status :ideal 900 :tolerance 300 :anchor 600 :status-strength 0.40}
       :interior {:classic 0.2 :modern 1.0 :cozy 0.6 :street-food 0.8 :premium 1.0}}}

     {:id :office-workers
      :population 300
      :order-rate-per-tick 0.012
      :outside-option-utility 0.50
      :behavior {:topping-variety-strength 0.10
                 :positioning-variety-strength 0.05}
      :preferences
      {:weights {:size 0.8 :seasoning 0.5 :topping 0.8 :price 1.5 :interior 0.6}
       :size {:small 0.6 :medium 1.0 :large 0.7 :extra-large 0.3}
       :seasoning {:salt 0.8 :pommesgewuerz 0.9}
       :topping {:none 0.2 :ketchup 0.8 :mayo 0.5 :ketchup-mayo 0.6
                 :special 0.5 :sate 0.5 :curry-ketchup 1.0 :garlic-sauce 0.7}
       :price {:model :value :ideal 525 :tolerance 175 :anchor 525
               :relative-price-strength 0.15}
       :interior {:classic 0.5 :modern 1.0 :cozy 0.6 :street-food 0.9 :premium 0.5}}}

     {:id :retirees
      :population 220
      :order-rate-per-tick 0.012
      :outside-option-utility 0.54
      :behavior {:topping-variety-strength 0.0
                 :positioning-variety-strength 0.0}
      :preferences
      {:weights {:size 0.8 :seasoning 0.4 :topping 0.6 :price 1.4 :interior 1.2}
       :size {:small 0.8 :medium 1.0 :large 0.6 :extra-large 0.2}
       :seasoning {:salt 1.0 :pommesgewuerz 0.4}
       :topping {:none 0.8 :ketchup 0.8 :mayo 0.9 :ketchup-mayo 0.7
                 :special 0.3 :sate 0.1 :curry-ketchup 0.3 :garlic-sauce 0.4}
       :price {:model :cheap :max 550 :anchor 475 :relative-price-strength 0.15}
       :interior {:classic 1.0 :modern 0.4 :cozy 1.0 :street-food 0.3 :premium 0.5}}}

     {:id :foodies
      :population 140
      :order-rate-per-tick 0.012
      :outside-option-utility 0.55
      :behavior {:topping-variety-strength 0.30
                 :positioning-variety-strength 0.25}
      :preferences
      {:weights {:size 0.7 :seasoning 1.2 :topping 1.5 :price 0.8 :interior 1.1}
       :size {:small 0.9 :medium 0.8 :large 0.5 :extra-large 0.2}
       :seasoning {:salt 0.3 :pommesgewuerz 1.0}
       :topping {:none 0.1 :ketchup 0.2 :mayo 0.3 :ketchup-mayo 0.2
                 :special 0.8 :sate 0.9 :curry-ketchup 0.7 :garlic-sauce 1.0}
       :price {:model :status :ideal 750 :tolerance 250 :anchor 750
               :status-strength 0.30}
       :interior {:classic 0.3 :modern 0.8 :cozy 0.8 :street-food 0.7 :premium 1.0}}}]}})

(defn simulation-config []
  (select-keys game-data [:game :rules :regulars :economy :prestige :research
                          :black-market :catalog :city :market :actions]))

(defn starter-setup []
  (get-in game-data [:setup :starter]))

(defn actions []
  (into {} (filter (comp :id val) (:actions game-data))))

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
