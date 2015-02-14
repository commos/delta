(ns commos.delta
  (:require [clojure.set :refer [difference]]))

(defn- collect
  [ks]
  (if (coll? ks) ks [ks]))

(defn add*
  [val [op korks-or-val new-val :as delta]]
  (if new-val
    (update-in val (collect korks-or-val) add* [op new-val])
    (let [new-val korks-or-val]
      (case op
        :is new-val
        :in (into (or val #{}) (collect new-val))
        :ex (if (map? val)
              (apply dissoc val (collect new-val))
              (difference val (collect new-val)))))))

(defn add
  "Reducing function to apply a delta to a streamed compound EDN
  value."
  ([] nil)
  ([val] val)
  ([val [op & maybe-deltas :as delta]]
   (if (#+clj identical? #+cljs keyword-identical? op :batch)
     (reduce add* val maybe-deltas)
     (add* val delta))))

;; Helpers on deltas
(defn pack
  "Wrap deltas in a :batch delta if necessary."
  [deltas]
  (if (> (count deltas) 1)
    (apply vector :batch deltas)
    (first deltas)))

(defn unpack
  "Return a seq of deltas from a delta."
  [[op & maybe-deltas :as delta]]
  (if (#+clj identical? #+cljs keyword-identical? op :batch)
    maybe-deltas
    [delta]))

(defn prepend-ks
  "Prepend ks to the keys in delta"
  [ks delta]
  (->> delta
       unpack
       (map (fn [[op korks-or-new-val new-val :as delta]]
              (if new-val
                [op (into ks (collect korks-or-new-val)) new-val]
                [op ks korks-or-new-val])))
       pack))

(defn diagnostic-delta
  "Bring delta into diagnostic form [op ks (v | [v+])].  v is a
  collection for :in and :ex ops.  This form is not applicable for add
  as ks can be [], which is invalid.

  Not suitable for :batch deltas (use unpack) beforehand."
  [[op korks-or-new-val new-val :as delta]]
  (cond-> (if new-val
            [op (collect korks-or-new-val) new-val]
            [op [] korks-or-new-val])
    (not= :is op) (update-in [2] collect)))

(defn normalized-delta
  "Bring a delta from diagnostic form into normalized form, applicable
  for add."
  [[op korks new-val :as delta]]
  (if (seq korks)
    delta
    [op new-val]))


;; Transducers
(defn nest
  "Returns a transducer that transforms deltas so that their paths
  begin with ks."
  [ks]
  (map (partial prepend-ks ks)))

(defn- results
  "Transducer for core/reductions with (f) semantics."
  ([f] (results f (f)))
  ([f init]
   (fn [rf]
     (let [state (volatile! ::uninitialized)]
       (fn
         ([] (rf))
         ([result] (rf (cond-> result
                         (and (= ::uninitialized @state))
                         (rf init))))
         ([result input]
          (if (#+clj identical? #+cljs keyword-identical?
                     ::uninitialized @state)
            (rf (rf result init)
                (vreset! state (f init input)))
            (rf result (vswap! state f input)))))))))

(def values
  "A stateful transducer that returns a new composite value for each
  delta going in."
  (results add))
