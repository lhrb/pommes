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
      (is (re-find #"Server: ready" (:body response)))
      (is (re-find #"Database: ready" (:body response))))))
