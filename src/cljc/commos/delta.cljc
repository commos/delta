(ns commos.delta
  (:require [clojure.set :as set]
            [commos.shared.core :refer [flatten-keys]]))

(def ^:private kw-identical?
  #?(:clj identical?
     :cljs keyword-identical?))

;; Helpers on deltas
(defn pack
  "Wrap raw deltas in a :batch delta if necessary."
  [deltas]
  (if (> (count deltas) 1)
    (apply vector :batch deltas)
    (first deltas)))

(defn unpack
  "Return a seq of raw deltas from delta."
  [[op & maybe-deltas :as maybe-delta]]
  (cond (kw-identical? op :batch)
        maybe-deltas
        maybe-delta
        [maybe-delta]))

(defn prepend-ks-raw
  "Like prepend-ks, but faster. Only applicable to a raw delta."
  [ks delta]
  (update delta 1 (partial into ks)))

(defn prepend-ks
  "Prepend ks to the keys in delta."
  [ks delta]
  (->> delta
       unpack
       (map (partial prepend-ks-raw ks))
       pack))

;; Creating deltas
(defn subtractive-deltas
  "Convert a map to subtractive raw deltas."
  [m]
  (let [step (fn step [root-ks [ks v]]
               (let [full-ks (into root-ks ks)]
                 (cond (map? v)
                       (mapcat (partial step full-ks) v)

                       (coll? v)
                       [[:ex full-ks v]]

                       :else
                       [[:ex (pop full-ks) [(peek full-ks)]]])))]
    (mapcat (partial step []) (flatten-keys m))))

(defn additive-deltas
  "Convert a map to additive raw deltas."
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

(defn- diff
  [a b]
  (if (= a b)
    [nil nil]
    (cond (and (map? a)
               (map? b))
          (reduce
           (fn [[in-a in-b :as acc] k]
             (let [v-in-a (get a k :sentinel)
                   v-in-b (get b k :sentinel)]
               (cond
                 (kw-identical? v-in-a :sentinel)
                 (assoc-in acc [1 k] v-in-b)

                 (kw-identical? v-in-b :sentinel)
                 (assoc-in acc [0 k] v-in-a)

                 (not= v-in-a v-in-b)
                 (if (or (nil? v-in-a)
                         (nil? v-in-b))
                   (-> acc
                       (assoc-in [0 k] v-in-a)
                       (assoc-in [1 k] v-in-b))

                   (let [[v-in-a v-in-b] (diff v-in-a v-in-b)]
                     (cond-> acc
                       v-in-a (assoc-in [0 k] v-in-a)
                       v-in-b (assoc-in [1 k] v-in-b))))

                 true
                 acc)))
           [nil nil]
           (eduction (distinct) (concat (keys a) (keys b))))
          (and (set? a)
               (set? b))
          [(not-empty (set/difference a b))
           (not-empty (set/difference b a))]
          :else
          [a b])))

(defn difference
  "Returns difference between x and y in raw deltas.  Adding the
  returned deltas to x gives y."
  [x y]
  (cond (and (map x) (map? y))
        (let [[neg pos] (diff x y)]
          (concat (subtractive-deltas neg)
                  (additive-deltas pos)))

        (and (set? x) (set? y))
        [[:in [] (set/difference y x)]]
        
        :else
        [[:is [] y]]))

(defn implied-deltas
  "Return deltas implied with (raw) delta so that no :is replacements
  of whole maps or sets, or :ex dissociations of whole maps are
  included."
  [val [op ks v :as delta]]
  (let [cur-v (get-in val ks)]
    (case op
      :is
      (->> (difference cur-v (first v))
           (map (partial prepend-ks-raw ks)))
      :ex
      (if (map? cur-v)
        (->> (difference cur-v (apply dissoc cur-v v))
             (map (partial prepend-ks-raw ks)))
        [delta])
      [delta])))

(defn- collect-ks
  [ks]
  (if (sequential? ks)
    ks
    [ks]))

(defn- collect-vs
  [vs]
  (if (or (sequential? vs)
          (set? vs))
    vs
    [vs]))

(defn- normalize-delta-ks
  "Ensure delta has a (possibly empty) vector of keys in second
  position."
  [[op ks-or-new-val new-val :as delta]]
  (if (= 3 (count delta))
    (update delta 1 collect-ks)
    (-> delta
        pop
        (conj [] ks-or-new-val))))

(defn- collect-delta-vs
  "If delta is :in or :ex type, ensures it has a sequence in third
  position."
  [[op :as delta]]
  (cond-> delta
    (or (kw-identical? op :in)
        (kw-identical? op :ex))
    (update 2 collect-vs)))

(defn- extract-delta
  "If delta is :on or :off pseudo-type, return an eduction (!) of raw deltas,
  otherwise return [delta]"
  [delta]
  (let [transform (fn [[_ ks v] f]
                    (eduction (map (partial prepend-ks-raw ks)) (f v)))]
    (case (first delta)
      :on (transform delta additive-deltas)
      :off (transform delta subtractive-deltas)
      [delta])))

(def raw-form
  "Transducer to create raw deltas from deltas in short form or raw
  deltas."
  (mapcat
   (fn [delta]
     {:pre [(vector? delta)
            (contains? #{:is :in :ex :on :off}
                       (first delta))]}
     (->> delta
          (normalize-delta-ks)
          (collect-delta-vs)
          (extract-delta)))))

(defn create
  "Utility for creating a delta.  Allows short form, :on and :off
  pseudo-deltas, raw form and does packing if necessary."
  [& deltas]
  (let [[_ d1 d2 :as bd] (into [:batch] raw-form deltas)]
    (if d2
      bd
      d1)))

;; Adding deltas
(declare add*)

(defn- add-raw-delta
  [val op new-val]
  (case op
    :is new-val
    :in (into (or val #{}) new-val)
    :ex (if (map? val)
          (apply dissoc val new-val)
          (set/difference val new-val))))

(defn- update-in'
  "Like update-in but auto dissocs the val at ks if it is an empty set
  or map, or nil."
  [m [k & ks] f & args]
  (let [r (if ks
            (apply update-in' (get m k) ks f args)
            (apply f (get m k) args))]
    (if (or (nil? r)
            (and (or (map? r)
                     (set? r))
                 (empty? r)))
      (dissoc m k)
      (assoc m k r))))

(defn- add*
  [val [op ks new-val :as delta]]
  (if (empty? ks)
    (add-raw-delta val op new-val)
    (update-in' val ks add-raw-delta op new-val)))

(defn add
  "Reducing function to add a delta to a streamed compound EDN value."
  ([] nil)
  ([val] val)
  ([val [op & maybe-deltas :as delta]]
   (if (kw-identical? op :batch)
     (reduce add* val maybe-deltas)
     (add* val delta))))

;; Transducers
(defn nest
  "Returns a transducer that transforms deltas so that their paths
  begin with ks.  Unpacks :batch deltas."
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
