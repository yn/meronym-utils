(defproject meronym-utils "1.0.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.2.1"]
                 [org.clojure/data.json "0.1.0"]
                 [org.jsoup/jsoup "1.4.1"]
                 [joda-time "1.6.2"]]
  :aot [meronym.condition.Condition]
  :dev-dependencies [[swank-clojure "1.3.0"]])

