(ns suurvay.async
  (:require [clojure.core.async :refer [<! chan go]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Macro
(defmacro while-let
  "bindings => binding-form test
  When test is true, evaluates body with binding-form bound to the
  value of test and then recurs."
  [bindings & body]
  (let [form (bindings 0) tst (bindings 1)]
    `(loop []
       (let [temp# ~tst]
         (when temp#
           (let [~form temp#]
             ~@body
             (recur)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Channel generator
(defn callback-chan
  "Returns a channel that calls `f` on every item put to the channel.
  Takes an optional argument `delay` which is a sleep time in msec to
  wait after taking each item before taking another."
  ([f] (callback-chan f nil))
  ([f delay]
   (let [ch (chan)]
     (go (while-let [item (<! ch)]
           (f item)
           (when delay
             (Thread/sleep delay))))
     ch)))

