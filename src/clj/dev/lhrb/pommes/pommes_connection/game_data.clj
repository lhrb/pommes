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
               :sate {:cost 0.60}}
    :interiors {:classic {}
                :modern {}
                :punk {}}}

   :setup
   {:dish-count 3
    :starter
    {:interior :classic
     :dishes [{:name "Classic pommes"
               :pommes {:size :small :seasoning :salt}
               :topping :ketchup
               :price 4.0}]}}

   :market
   {:price-anchors {:students 4.50
                    :families 5.50
                    :yuppies 6.00}}

   :city
   {:demographics
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
       :topping {:none 0.3 :ketchup 1.0 :mayo 0.7 :ketchup-mayo 0.9 :special 0.4 :sate 0.5}
       :price {:model :cheap :max 5.00 :anchor 4.50 :relative-price-strength 0.25}
       :interior {:classic 0.5 :modern 0.8 :punk 1.0}}}

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
       :topping {:none 0.5 :ketchup 1.0 :mayo 0.8 :ketchup-mayo 0.9 :special 0.5 :sate 0.2}
       :price {:model :value :ideal 5.50 :tolerance 2.25 :anchor 5.50 :relative-price-strength 0.10}
       :interior {:classic 1.0 :modern 0.8 :punk 0.2}}}

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
       :topping {:none 0.2 :ketchup 0.1 :mayo 0.2 :ketchup-mayo 0.1 :special 1.0 :sate 0.8}
       :price {:model :status :ideal 9.00 :tolerance 3.00 :anchor 6.00 :status-strength 0.40}
       :interior {:classic 0.2 :modern 1.0 :punk 0.6}}}]}})

(defn simulation-config []
  (select-keys game-data [:rules :catalog :city :market]))

(defn starter-setup []
  (get-in game-data [:setup :starter]))

(comment
  ;; Add a topping, interior, or demographic by editing game-data.
  (keys (get-in game-data [:catalog :toppings])))
