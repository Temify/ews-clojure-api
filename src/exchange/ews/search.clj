(ns exchange.ews.search
  (:require [exchange.ews.authentication :refer [service-instance]]
            [exchange.ews.util :refer [load-property-set default-property-set do-while]])
  (:import (clojure.lang Reflector)
           (microsoft.exchange.webservices.data.core PropertySet)
           (microsoft.exchange.webservices.data.core.enumeration.property WellKnownFolderName)
           (microsoft.exchange.webservices.data.core.enumeration.search LogicalOperator)
           (microsoft.exchange.webservices.data.core.service.schema ItemSchema
                                                                    EmailMessageSchema)
           (microsoft.exchange.webservices.data.property.complex MessageBody)
           (microsoft.exchange.webservices.data.search ItemView)
           (microsoft.exchange.webservices.data.search.filter SearchFilter
                                                              SearchFilter$SearchFilterCollection
                                                              SearchFilter$ContainsSubstring
                                                              SearchFilter$IsEqualTo
                                                              SearchFilter$IsGreaterThan)))

(def ^{:doc "Search filters available in EWS API"} search-filters
  {:contains-substring SearchFilter$ContainsSubstring
   :is-equal SearchFilter$IsEqualTo
   :is-greater-then SearchFilter$IsGreaterThan})

(def ^{:doc "Logical operators available for filtering"} operators
  {:or LogicalOperator/Or
   :and LogicalOperator/And})

(defn transform-search-result
  "Transforms search result into vector of Clojure maps"
  [items]
  (map #(hash-map :id (.getUniqueId (.getId %))
                  :subject (.getSubject %)
                  :body (-> (.getBody %)
                            MessageBody/getStringFromMessageBody)
                  :date-received (.getDateTimeReceived %)) items))

(defn list-paginated-items
  "Get page of items defined by offset (defaults to 0). Folder id defaults to Inbox"
  ([limit]
   (list-paginated-items WellKnownFolderName/Inbox limit 0))
  ([limit offset]
   (list-paginated-items WellKnownFolderName/Inbox limit offset))
  ([folder-id limit offset]
   (let [view (ItemView. limit offset)
         result (.findItems @service-instance folder-id view)]
     (load-property-set result)
     (.getItems result))))

(defn list-all-items
  "List all items in folder without pagination. Folder id can be both string id or enumeration of well know name,
  defaults to Inbox"
  ([]
   (list-all-items WellKnownFolderName/Inbox))
  ([folder-id]
   (let [view (ItemView. Integer/MAX_VALUE)
         result (.findItems @service-instance folder-id view)]
     (load-property-set result)
     (.getItems result))))

(defn create-filter-collection
  "Creates filter collection, operator value can be :and or :or. Filters should be collection filters created via
  create-search-filter function"
  [operator filters]
  {:pre? (contains? operators operator)}
  (SearchFilter$SearchFilterCollection. (get operator operators)
                                        (into-array filters)))

(defn create-search-filter
  "Filter type has to be one of filters defined in search-filters map. Search field has to be value from either ItemSchema
  or EmailMessageSchema enum. Returns instance of one of the SearchFilter nested class"
  [filter-type search-field search-value]
  (let [ews-filter (get search-filters filter-type)]
    (Reflector/invokeConstructor ews-filter (object-array [search-field search-value]))))

(defn get-emails-by-recipient
  "Returns all emails filtered by receipient"
  [email]
  (let [view (ItemView. Integer/MAX_VALUE)
        filters [(create-search-filter :contains-substring EmailMessageSchema/From email)]
        result (.findItems @service-instance WellKnownFolderName/Inbox
                           (create-filter-collection :or filters) view)]
    (load-property-set result)
    (.getItems result)))