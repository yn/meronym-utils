(ns meronym.ring-utils
  (:use [clojure.pprint :only [pprint]])
  (:require [clojure.contrib.json :as json]
            [ring.util.codec :as codec])
  (:import [java.io InputStreamReader BufferedReader]
           java.security.SecureRandom
           [org.jsoup Jsoup nodes.Document nodes.Entities$EscapeMode
            safety.Cleaner safety.Whitelist]
           java.util.logging.Logger
           org.apache.commons.io.IOUtils
           org.joda.time.DateTime))

(defprotocol DeploymentEnvironment
  (production? [x]))

(defn wrap-deployment-environment [app de]
  (fn [req]
    (app (with-meta req (assoc (meta req) :de de)))))

(defn production-deployment? [req]
  (-> (meta req) :de (production?)))

(defn response
  "This is a helper function for constructing Ring responses. It returns a Ring
   response map which automatically converts maps to JSON strings, and tries its
   best to guess the correct Content-Type value."
  [body & {:keys [status content-type session]
           :or {status 200}}]
  (let [[body content-type] (cond
                             ;; maps convert to JSON
                             (map? body)
                             [(json/json-str body) "application/json"]
                             ;; strings convert to plain text (or the given type)
                             (string? body)
                             [body (or content-type "text/plain")]
                             ;; everything else
                             :else [body "application/octet-stream"])
        resp {:status status
              :headers {"Content-Type" content-type}
              :body body}]
    (if (and (map? session) (not (empty? session)))
        (assoc resp :session session)
        resp)))


(defn get-content
  "This function retrieves the content of a Ring request map, trying its best to
   guess the correct type for the response value. JSON gets converted to a map,
   strings become strings (or a sequence of lines if :line-seq? is true), and
   everything else becomes a byte array."
  [req & {:keys [line-seq?] :or {line-seq? false}}]
  (let [content-type (:content-type req)]
    (cond
     ;; use lazy line-seq
     (and (re-matches #"text\/.*" content-type) line-seq?)
     (line-seq (-> (:body req) InputStreamReader. BufferedReader.))
     ;; return whole string at once
     (re-matches #"text\/.*" content-type)
     (IOUtils/toString (:body req) (:character-encoding req))
     ;; JSON
     (re-matches #"application\/json.*" content-type)
     (json/read-json (IOUtils/toString (:body req) (:character-encoding req)))
     ;; no idea about the content-type, just return a byte array
     :else (IOUtils/toByteArray (:body req)))))


(defn wrap-stacktrace-log [app level & {:keys [log-servlet-request-info?]
                                        :or {log-servlet-request-info? false}}]
  (let [logger (Logger/getLogger (name level))]
    (fn [req]
      (try
        (app req)
        (catch Exception e
          (let [exception-text (with-open [sw (java.io.StringWriter.)
                                           pw (java.io.PrintWriter. sw)]
                                 (.printStackTrace e pw)
                                 (.toString sw))]
            (when log-servlet-request-info?
              (.severe logger (str (:request req))))
            (.severe logger exception-text)
            ;; The response should contain the stack trace in non-production
            ;; mode.
            (response (if (production-deployment? req)
                          ""
                          exception-text)
                      :status 500)))))))


(let [filter-sensitive (fn [params]
                         (into {} (map (fn [[k v]]
                                         (if (.contains (str k) "password")
                                             [k :filtered]
                                             [k v]))
                                       params)))]
  (defn- request-log-entry [req resp]
    (format
     (if (production-deployment? req)
         "%s %s \"%s\" %d\nsession %scsrf \"%s\"\nparams %sunsanitized %s%s"
         "%s %s \"%s\" %d; session %s; csrf \"%s\"; params %s; unsanitized %s%s")
     (DateTime.)
     (-> req :request-method name .toUpperCase)
     (:uri req)
     (:status resp)
     (if (production-deployment? req)
         (with-out-str (pprint (filter-sensitive (:session req))))
         (filter-sensitive (:session req)))
     (-> req :cookies (get "_csrf_token") :value)
     (if (production-deployment? req)
         (with-out-str (pprint (filter-sensitive (:params req))))
         (filter-sensitive (:params req)))
     (if (production-deployment? req)
         (with-out-str (pprint (filter-sensitive (:unsanitized-params req))))
         (filter-sensitive (:unsanitized-params req)))
     (if (contains? (:headers req) "referer")
         (str (if (production-deployment? req) "" "; ")
              "referrer " (get (:headers req) "referer"))
         ""))))


(defn wrap-request-log [app]
  (let [logger (Logger/getLogger "application")]
    (fn [req]
      (let [resp (app req)]
        (.info logger (request-log-entry req resp))
        resp))))


(defn wrap-whitelist-sanitize [app]
  (let [cleaner (Cleaner. (Whitelist/none))
        sanitize (fn [input]
                   (let [dirty (Jsoup/parseBodyFragment input "")
                         clean (.. cleaner (clean dirty))]
                     (.. clean outputSettings (escapeMode Entities$EscapeMode/xhtml))
                     (.. clean body html)))]
    (fn [{params :params :as req}]
      (let [new-params (into {} (map (fn [[k v]] [k (sanitize v)]) params))
            new-req (assoc req :unsanitized-params params :params new-params)
            resp (app new-req)]
        resp))))


(defn wrap-request-forgery-protection [app & {:keys [except] :or {except #{}}}]
  (let [except (if (set? except) except (set except))
        logger (Logger/getLogger "application")]
    (fn [req]
      (let [{:keys [uri params request-method cookies]} req]
        (if (or (contains? #{:get :head} request-method)
                (some #(.startsWith uri %) except))
            (app req)
            (if (and (:value (cookies "_csrf_token"))
                     (:authenticity-token params)
                     (= (:authenticity-token params) (:value (cookies "_csrf_token"))))
                (app req)
                (let [resp (response "cross-site request forgery detected" :status 403)]
                  (.warning logger (str "cross-site request forgery detected: "
                                        (request-log-entry req resp)))
                  resp)))))))


(defn- secure-random-bytes-64
  "Returns a random byte array of the specified size encoded to base64"
  [size]
  (let [seed (byte-array size)]
    (.nextBytes (SecureRandom/getInstance "SHA1PRNG") seed)
    (codec/base64-encode seed)))


(defn csrf-token [_]
  (assoc (response "")
    :cookies {"_csrf_token" {:path "/"
                             :value (secure-random-bytes-64 32)}}))
