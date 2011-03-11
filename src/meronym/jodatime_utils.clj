(ns meronym.jodatime-utils
  (:import [org.joda.time DateMidnight DateTime Seconds Minutes Hours Days Months Years]))

(defn today []
  (.. (DateMidnight.) toDate))

(defn now []
  (.. (DateTime.) toDate))

(defn ago [& {:keys [years months days] :or {years 0 months 0 days 0}}]
  (.. (DateMidnight.) (minusYears years) (minusMonths months) (minusDays days) toDate))

(defn from-now [& {:keys [years months days] :or {years 0 months 0 days 0}}]
  (.. (DateMidnight.) (plusYears years) (plusMonths months) (plusDays days) toDate))

(defn seconds-since [date1]
  (. (Seconds/secondsBetween (DateTime. date1) (DateTime.)) getSeconds))

(defn minutes-since [date1]
  (. (Minutes/minutesBetween (DateTime. date1) (DateTime.)) getMinutes))

(defn hours-since [date1]
  (. (Hours/hoursBetween (DateTime. date1) (DateTime.)) getHours))

(defn days-since [date1]
  (. (Days/daysBetween (DateTime. date1) (DateTime.)) getDays))

(defn months-since [date1]
  (. (Months/monthsBetween (DateTime. date1) (DateTime.)) getMonths))

(defn years-since [date1]
  (. (Years/yearsBetween (DateTime. date1) (DateTime.)) getYears))

(defn years-ago [years]
  (ago :years years))

(defn days-ago [days]
  (ago :days days))
