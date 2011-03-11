(ns meronym.utils)


(defmacro maybe-nil [& e]
  `(try ~@e (catch Exception _# nil)))
