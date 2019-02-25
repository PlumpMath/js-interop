;; Some docstrings copied and/or adapted from ClojureScript, which is copyright (c) Rich Hickey.
;;   See https://github.com/clojure/clojurescript/blob/master/src/main/cljs/cljs/core.cljs

(ns applied-science.js-interop
  "Functions for working with JavaScript that mirror Clojure behaviour."
  (:refer-clojure :exclude [get get-in assoc! assoc-in! update! update-in! select-keys contains? unchecked-get unchecked-set apply])
  (:require [goog.object :as gobj]
            [goog.reflect]
            [cljs.core :as core])
  (:require-macros [applied-science.js-interop :as j]))

(def lookup-sentinel #js{})

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Key conversion
;;
;; Throughout this namespace, k* and ks* refer to keys that have already been wrapped.

(defn wrap-key
  "Returns `k` or, if it is a keyword, its name."
  [k]
  (cond-> k
          (keyword? k) (name)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Unchecked operations

(defn unchecked-set [obj k val]
  (core/unchecked-set obj (wrap-key k) val)
  obj)

(defn unchecked-get [obj k]
  (core/unchecked-get obj (wrap-key k)))

(defn ^boolean contains?* [obj k*]
  (gobj/containsKey obj k*))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Lookups

(defn get
  "Returns the value mapped to key, not-found or nil if key not present."
  ([obj k]
   (j/get obj k))
  ([obj k not-found]
   (j/get obj k not-found)))

(defn- get-value-by-keys
  "Look up `ks` in `obj`, stopping at any nil"
  [obj ks*]
  (when obj
    (let [end (count ks*)]
      (loop [i 0
             obj obj]
        (if (or (= i end)
                (nil? obj))
          obj
          (recur (inc i)
                 (core/unchecked-get obj (nth ks* i))))))))

(defn get-in*
  ([obj ks*]
   (get-value-by-keys obj ks*))
  ([obj ks* not-found]
   (if-some [last-obj (get-value-by-keys obj (butlast ks*))]
     (j/get last-obj (peek ks*) not-found)
     not-found)))

(defn get-in
  "Returns the value in a nested object structure, where ks is
   a sequence of keys. Returns nil if the key is not present,
   or the not-found value if supplied."
  ([obj ks]
   (get-in* obj (mapv wrap-key ks)))
  ([obj ks not-found]
   (get-in* obj (mapv wrap-key ks) not-found)))

(deftype JSLookup [obj]
  ILookup
  (-lookup [_ k]
    (j/get obj k))
  (-lookup [_ k not-found]
    (j/get obj k not-found))
  IDeref
  (-deref [o] obj))

(defn lookup
  "Returns object which implements ILookup and reads keys from `obj`."
  [obj]
  (JSLookup. obj))

(defn ^boolean contains? [obj k]
  (contains?* obj (wrap-key k)))

(defn select-keys*
  "Returns an object containing only those entries in `o` whose key is in `ks`"
  [obj ks*]
  (->> ks*
       (reduce (fn [m k]
                 (cond-> m
                         ^boolean (gobj/containsKey obj k)
                         (doto
                           (core/unchecked-set k
                                               (core/unchecked-get obj k))))) #js {})))

(defn select-keys
  "Returns an object containing only those entries in `o` whose key is in `ks`"
  [obj ks]
  (select-keys* obj (mapv wrap-key ks)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Mutations

(defn assoc!
  "Sets key-value pairs on `obj`, returns `obj`."
  [obj & pairs]
  (let [obj (if (some? obj) obj #js{})]
    (loop [[k v & kvs] pairs]
      (unchecked-set obj k v)
      (if kvs
        (recur kvs)
        obj))))

(defn assoc-in*
  [obj [k* & ks*] v]
  (let [obj (if (some? obj) obj #js{})]
    (core/unchecked-set obj k*
                        (if ks*
                          (assoc-in* (core/unchecked-get obj k*) ks* v)
                          v))
    obj))

(defn assoc-in!
  "Mutates the value in a nested object structure, where ks is a
  sequence of keys and v is the new value. If any levels do not
  exist, objects will be created."
  [obj [k & ks] v]
  (if ks
    (assoc! obj k (assoc-in! (j/get obj k) ks v))
    (assoc! obj k v)))

(defn update!
  "'Updates' a value in a JavaScript object, where k is a key and
  f is a function that will take the old value and any supplied
  args and return the new value, which replaces the old value.
  If the key does not exist, nil is passed as the old value."
  [obj k f & args]
  (let [obj (if (some? obj) obj #js{})
        k* (wrap-key k)
        v (core/apply f (core/unchecked-get obj k*) args)]
    (core/unchecked-set obj k* v)
    obj))

(defn update-in*
  [obj ks* f args]
  (let [obj (if (some? obj) obj #js{})
        old-val (get-value-by-keys obj ks*)]
    (assoc-in* obj ks* (core/apply f old-val args))))

(defn update-in!
  "'Updates' a value in a nested object structure, where ks is a
  sequence of keys and f is a function that will take the old value
  and any supplied args and return the new value, mutating the
  nested structure.  If any levels do not exist, objects will be
  created."
  [obj ks f & args]
  (update-in* obj (mapv wrap-key ks) f args))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Array operations

(defn push! [^js a v]
  (doto a
    (.push v)))

(defn unshift! [^js a v]
  (doto a
    (.unshift v)))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Function operations

(defn call [obj k & args]
  (.apply (j/get obj k) obj (to-array args)))

(defn apply [obj k arg-array]
  (.apply (j/get obj k) obj arg-array))

;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Object creation

(defn obj
  "Create JavaSript object from an even number arguments representing
   interleaved keys and values. Dot-prefixed symbol keys will be renamable."
  [& keyvals]
  (let [obj (js-obj)]
    (doseq [[k v] (partition 2 keyvals)]
      (j/assoc! obj k v))
    obj))
