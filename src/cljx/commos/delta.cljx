(ns commos.delta
  (:require [clojure.set :refer [difference]]
            [commos.shared.core :refer [flatten-keys]]))

(defn- collect
  [ks]
  (if (coll? ks) ks [ks]))

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
              (if (= 3 (count delta))
                [op (into ks (collect korks-or-new-val)) new-val]
                [op ks korks-or-new-val])))
       pack))

(defn diagnostic-delta
  "Bring delta into diagnostic form [op ks (v | [v+])].  v is a
  collection for :in and :ex ops.  This form is not applicable for add
  as ks can be [], which is invalid.  See summable-delta.

  Not suitable for :batch deltas (use unpack) beforehand."
  [[op korks-or-new-val new-val :as delta]]
  (cond-> (if (= 3 (count delta))
            [op (collect korks-or-new-val) new-val]
            [op [] korks-or-new-val])
    (not (#+clj identical? #+cljs keyword-identical? op :is))
    (update-in [2] collect)))

(defn summable-delta
  "Make a diagnostic applicable for add again."
  [[op korks new-val :as delta]]
  (if (seq korks)
    delta
    [op new-val]))

;; Creating deltas
(defn negative-diagnostic-deltas
  "Convert a map to negative diagnostic deltas."
  [m]
  (let [step (fn step [root-ks [ks v]]
               (let [full-ks (into root-ks ks)]
                 (cond (map? v)
                       (mapcat (partial step full-ks) v)

                       (coll? v)
                       [[:ex full-ks v]]

                       :else
                       [[:ex (pop full-ks) (peek full-ks)]])))]
    (mapcat (partial step []) (flatten-keys m))))

(defn negative-deltas
  "Convert a map to negative summable deltas."
  [m]
  (->> m
       negative-diagnostic-deltas
       (map summable-delta)))

(defn positive-diagnostic-deltas
  "Convert a map to positive diagnostic deltas."
  [m]
  (let [step (fn step [root-ks [ks v]]
               (let [full-ks (into root-ks ks)]
                 (cond (map? v)
                       (mapcat (partial step full-ks) v)

                       (coll? v)
                       [[:in full-ks v]]

                       :else
                       [[:is full-ks v]])))]
    (mapcat (partial step []) (flatten-keys m))))

(def ^{:arglists '([m])} positive-deltas
  "Convert a map to positive summable deltas."
  positive-diagnostic-deltas)

;; Transducers
(defn nest
  "Returns a transducer that transforms deltas so that their paths
  begin with ks."
  [ks]
  (map (partial prepend-ks ks)))

(defn- results-skip-init
  "Transducer for core/reductions with (f) semantics. Doesn't produce
  init value."
  ([f] (results-skip-init f (f)))
  ([f init]
   (fn [rf]
     (let [state (volatile! init)]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (rf result (vswap! state f input))))))))

(def values
  "A stateful transducer that returns a new composite value for each
  delta going in."
  (results-skip-init add))

;; Adding deltas
(declare add*)

(defn- add-root-delta
  [val op new-val]
  (case op
    :is new-val
    :in (into (or val #{}) (collect new-val))
    :ex (if (map? val)
          (apply dissoc val (collect new-val))
          (difference val (collect new-val)))

    :on (reduce add* val (positive-deltas new-val))
    :off (reduce add* val (negative-deltas new-val))))

(defn- add*
  [val [op korks-or-new-val new-val :as delta]]
  (if (= 3 (count delta))
    (update-in val (collect korks-or-new-val)
               add-root-delta op new-val)
    (add-root-delta val op korks-or-new-val)))

(defn add
  "Reducing function to add a delta to a streamed compound EDN value."
  ([] nil)
  ([val] val)
  ([val [op & maybe-deltas :as delta]]
   (if (#+clj identical? #+cljs keyword-identical? op :batch)
     (reduce add* val maybe-deltas)
     (add* val delta))))
