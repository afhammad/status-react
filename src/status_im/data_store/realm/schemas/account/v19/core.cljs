(ns status-im.data-store.realm.schemas.account.v19.core
  (:require [status-im.data-store.realm.schemas.account.v11.chat :as chat]
            [status-im.data-store.realm.schemas.account.v1.chat-contact :as chat-contact]
            [status-im.data-store.realm.schemas.account.v6.command :as command]
            [status-im.data-store.realm.schemas.account.v9.command-parameter :as command-parameter]
            [status-im.data-store.realm.schemas.account.v19.contact :as contact]
            [status-im.data-store.realm.schemas.account.v1.discover :as discover]
            [status-im.data-store.realm.schemas.account.v1.kv-store :as kv-store]
            [status-im.data-store.realm.schemas.account.v10.message :as message]
            [status-im.data-store.realm.schemas.account.v12.pending-message :as pending-message]
            [status-im.data-store.realm.schemas.account.v1.processed-message :as processed-message]
            [status-im.data-store.realm.schemas.account.v15.request :as request]
            [status-im.data-store.realm.schemas.account.v1.tag :as tag]
            [status-im.data-store.realm.schemas.account.v1.user-status :as user-status]
            [status-im.data-store.realm.schemas.account.v5.contact-group :as contact-group]
            [status-im.data-store.realm.schemas.account.v5.group-contact :as group-contact]
            [status-im.data-store.realm.schemas.account.v8.local-storage :as local-storage]
            [status-im.data-store.realm.schemas.account.v13.handler-data :as handler-data]
            [taoensso.timbre :as log]
            [cljs.reader :as reader]))

(def schema [chat/schema
             chat-contact/schema
             command/schema
             command-parameter/schema
             contact/schema
             discover/schema
             kv-store/schema
             message/schema
             pending-message/schema
             processed-message/schema
             request/schema
             tag/schema
             user-status/schema
             contact-group/schema
             group-contact/schema
             local-storage/schema
             handler-data/schema])

(defn remove-contact! [new-realm whisper-identity]
  (when-let [contact (some-> new-realm
                             (.objects "contact")
                             (.filtered (str "whisper-identity = \"" whisper-identity "\""))
                             (aget 0))]
    (log/debug "v19 Removing contact" (pr-str contact))
    (.delete new-realm contact)))

(def owner-command->new-props
  {;; console commands
   ["console" "password"] {:content-command-ref ["console" :response 10 "password"]
                           :content-command-scope-bitmask 10}
   ["console" "debug"] {:content-command-ref ["console" :command 26 "debug"]
                        :content-command-scope-bitmask 26}
   ["console" "phone"] {:content-command-ref ["console" :response 18 "phone"]
                        :content-command-scope-bitmask 18}
   ["console" "confirmation-code"] {:content-command-ref ["console" :response 18 "confirmation-code"]
                                    :content-command-scope-bitmask 18}
   ["console" "faucet"] {:content-command-ref ["console" :command 18 "faucet"]
                         :content-command-scope-bitmask 18}
   ;; mailman commands
   ["mailman" "location"] {:content-command-ref ["mailman" :command 55 "location"]
                           :content-command-scope-bitmask 55}
   ;; transactor personal
   ["transactor-personal" "send"] {:content-command-ref ["transactor" :command 51 "send"]
                                   :content-command-scope-bitmask 51
                                   :bot "transactor"}
   ["transactor-personal" "request"] {:content-command-ref ["transactor" :command 51 "request"]
                                      :content-command-scope-bitmask 51
                                      :bot "transactor"}
   ;; transactor group
   ["transactor-group" "send"] {:content-command-ref ["transactor" :command 53 "send"]
                                :content-command-scope-bitmask 53
                                :bot "transactor"}
   ["transactor-group" "request"] {:content-command-ref ["transactor" :command 53 "request"]
                                   :content-command-scope-bitmask 53
                                   :bot "transactor"}})

(def console-requests->new-props
  {;; console
   ["password"] {:content-command-ref ["console" :response 10 "password"]}
   ["phone"] {:content-command-ref ["console" :response 18 "phone"]}
   ["confirmation-code"] {:content-command-ref ["console" :response 18 "confirmation-code"]}})

(defn update-commands [selector mapping new-realm content-type]
  (some-> new-realm
          (.objects "message")
          (.filtered (str "content-type = \"" content-type "\""))
          (.map (fn [object _ _]
                  (let [content (reader/read-string (aget object "content"))
                        new-props (get owner-command->new-props (selector content))
                        new-content (merge content new-props)]
                    (log/debug "migrating v19 command/request database, updating: " content " with: " new-props)
                    (aset object "content" (pr-str new-content)))))))

(defn migration [old-realm new-realm]
  (log/debug "migrating v19 account database: " old-realm new-realm)
  (remove-contact! new-realm "transactor-personal")
  (remove-contact! new-realm "transactor-group")
  (update-commands owner-command->new-props (juxt :bot :command) new-realm "command")
  (update-commands console-requests->new-props (juxt :command) new-realm "command-request"))
