(ns commos.delta
  (:require [clojure.set :refer [difference]]
            [commos.shared.core :refer [flatten-keys]]))

(defn- collect
  [ks]
  (if (coll? ks) ks [ks]))

;; Helpers on deltas
(defn summable-delta
  "Make a diagnostic applicable for add again."
  [[op korks new-val :as delta]]
  (let [new-val (cond-> new-val
                  (= :is op) peek)]
    (if (seq korks)
      (assoc delta 2 new-val)
      [op new-val])))

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
                       [[:is full-ks [v]]])))]
    (mapcat (partial step []) (flatten-keys m))))

(defn positive-deltas
  "Convert a map to positive summable deltas."
  [m]
  (->> m
       positive-diagnostic-deltas
       (map summable-delta)))

;; Helpers on deltas
(defn diagnostic-delta
  "Bring delta into diagnostic form [op ks vs].  For convenience, vs
  is a collection for any op.

  This form is not applicable for add.  See summable-delta.

  Not suitable for :batch - use unpack."
  [[op korks-or-new-val new-val :as delta]]
  (-> (if (= 3 (count delta))
        [op (collect korks-or-new-val) new-val]
        [op [] korks-or-new-val])
      (update-in [2]
                 (if (#+clj identical? #+cljs keyword-identical? op :is)
                   vector
                   collect))))

(defn- prepend-ks*
  [ks [op korks-or-new-val new-val :as delta]]
  (if (= 3 (count delta))
    [op (into ks (collect korks-or-new-val)) new-val]
    [op ks korks-or-new-val]))

(defn pack
  "Wrap deltas in a :batch delta if necessary."
  [deltas]
  (if (> (count deltas) 1)
    (apply vector :batch deltas)
    (first deltas)))

(defn- unpack-batch
  [[op & maybe-deltas :as delta]]
  (if (#+clj identical? #+cljs keyword-identical? op :batch)
    maybe-deltas
    [delta]))

(defn unpack
  "Return a seq of deltas from a delta.  Returns a seq with
  only :is, :in and :ex deltas."
  [delta]
  (let [transform (fn [delta f]
                    (let [[_ ks v] (diagnostic-delta delta)]
                      (->> v
                           f
                           (map (partial prepend-ks* ks))
                           (map summable-delta))))]
    (->> delta
         unpack-batch
         (mapcat (fn [[op :as delta]]
                   (case op
                     :on (transform delta
                                    positive-diagnostic-deltas)
                     :off (transform delta
                                     negative-diagnostic-deltas)
                     [delta]))))))

(defn prepend-ks
  "Prepend ks to the keys in delta"
  [ks delta]
  (->> delta
       unpack
       (map (partial prepend-ks* ks))
       pack))

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
