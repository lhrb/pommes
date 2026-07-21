(ns dev.lhrb.pommes.pommes-connection.money
  (:require [clojure.string :as str])
  (:import [java.math BigDecimal RoundingMode]))

(defn parse-euros
  "Parses a non-negative euro amount with at most two decimal places to cents."
  [value]
  (let [value (some-> value str str/trim)]
    (when (and value (re-matches #"\d+(?:\.\d{1,2})?" value))
      (try
        (let [[euros cents] (str/split value #"\." 2)
              cents (str (or cents "") "00")]
          (Long/parseLong (str euros (subs cents 0 2))))
        (catch NumberFormatException _ nil)))))

(defn round-cents
  "Rounds a computed monetary value to the nearest cent."
  [value]
  (.longValueExact (.setScale (BigDecimal/valueOf (double (or value 0)))
                               0
                               RoundingMode/HALF_UP)))

(defn format-euros
  [cents]
  (let [cents (long (or cents 0))
        negative? (neg? cents)
        amount (if negative? (- cents) cents)]
    (format "%s€%d,%02d" (if negative? "-" "") (quot amount 100) (mod amount 100))))

(defn euros-input
  [cents]
  (let [cents (long (or cents 0))]
    (format "%d.%02d" (quot cents 100) (mod cents 100))))

(comment
  (parse-euros "4.50")
  ;; => 450
  (format-euros 450)
  ;; => "€4,50"
  (euros-input 450)
  ;; => "4.50"
  (round-cents 12.5)
  ;; => 13
  )
