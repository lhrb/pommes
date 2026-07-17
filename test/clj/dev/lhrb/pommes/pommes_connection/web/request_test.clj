(ns dev.lhrb.pommes.pommes-connection.web.request-test
  (:require  [clojure.test :refer [deftest testing is use-fixtures]]
             [dev.lhrb.pommes.pommes-connection.test-utils :refer [system-state system-fixture GET]]))

(use-fixtures :once (system-fixture))

(deftest health-request-test []
  (testing "happy path"
    (let [handler (:app/web (system-state))
          params {}
          headers {}
          response (GET handler "/health" params headers)]
      (is (= 200 (:status response)))
      (is (re-find #"Server: bereit" (:body response)))
      (is (re-find #"Datenbank: bereit" (:body response))))))

(deftest static-asset-request-test []
  (let [handler (:app/web (system-state))
        response (GET handler "/assets/css/app.css" {} {})]
    (is (= 200 (:status response)))
    (is (re-find #"text/css" (get-in response [:headers "Content-Type"])))
    (is (re-find #"background\.svg" (:body response)))))
