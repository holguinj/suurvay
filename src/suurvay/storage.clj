(ns suurvay.storage
  "Contains the storage protocol and common functions only.")

(defprotocol Storage
  ;; Storage Methods
  (store-user [this user] "Store a user object for later retrieval.")
  (store-status [this status] "Store a status object for later retrieval.")
  (store-timeline [this timeline] "Store a timeline for later retrieval.")

  ;; Retrieval Methods
  (get-user [this id] "Retrieves a user object from storage.")
  (get-status [this id] "Retrieves a status object from storage.")
  (get-timeline [this id] "Retrieves all available statuses for the specified user."))

