(ns zulip-focus.bot
  (:gen-class)
  (:require [clj-http.client :as client]
            [clojure.data.json :as json])
  )


(defn create-queue [config]
  (json/read-str (:body (client/post (:create_queue_url config)
                        {
                         :basic-auth [(:zulip_bot_email config) (:zulip_api_key config)]
                         :form-params { :event_types "[\"message\"]" }
                        }))
                 :key-fn keyword))

(defn ensure-queue [config]
  (try (client/get (:read_url config)
              {
                :basic-auth [(:zulip_bot_email config) (:zulip_api_key config)]
                :query-params { "queue_id" (:queue_id config) "last_event_id" "-1" }
              })
         config
    (catch clojure.lang.ExceptionInfo e
      (let [params (create-queue config)]
        (assoc config :queue_id (:queue_id params))))))

(defn load-config [filename]
    (let [config (read-string (slurp filename))
         updated-config (ensure-queue config)]
         (spit filename (with-out-str (pr updated-config)))
        updated-config))


(def config (load-config "config.clj"))

(defn parse-events [events]
  (map (fn [event]
         (let [message (:message event)]
           (str "[" (:display_recipient message) "/" (:subject message) "] " (:sender_full_name message) ": " (:content message))))
       events))

(defn get-once [last_event_id]
  (let [message (client/get (:read_url config)
              {
                :basic-auth [(:zulip_bot_email config) (:zulip_api_key config)]
                :query-params { "queue_id" (:queue_id config) "last_event_id" last_event_id }
              })
        body (json/read-str (:body message) :key-fn keyword)
        event-type (:type (first (:events body)))]
    (if (not (= event-type "heartbeat"))
      (do
          (println (parse-events(:events body)))
          (str (:id (last (:events body))))
        )
      last_event_id
     )

    )

  )

(defn get-forever [last_event_id]
  (recur (get-once last_event_id)))


(defn -main [& args]
  (println "Starting...")
  (get-forever "-1"))
