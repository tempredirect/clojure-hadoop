(ns clojure-hadoop.wrap
  ;;#^{:doc "Map/Reduce wrappers that set up common input/output
  ;;conversions for Clojure jobs."}
  (:require [clojure-hadoop.imports :as imp]))

(imp/import-io)
(imp/import-mapred)

(declare *reporter*)

(defn- non-caching-iterator-seq [#^java.util.Iterator iter]
  (proxy [clojure.lang.ISeq] []
    (seq [] this)
    (first [] (when (.hasNext iter) (.next iter)))
    (next [] (when (.hasNext iter) this))
    (more [] (if (.hasNext iter) this (list)))))

(defn string-map-reader
  "Returns a [key value] pair by calling .toString on the Writable key
  and value."
  [wkey wvalue]
  [(.toString wkey) (.toString wvalue)])

(defn int-string-map-reader [wkey wvalue]
  [(.get wkey) (.toString wvalue)])

(defn clojure-map-reader
  "Returns a [key value] pair by calling read-string on the string
  representations of the Writable key and value."
  [wkey wvalue]
  [(read-string (.toString wkey)) (read-string (.toString wvalue))])

(defn clojure-reduce-reader
  "Returns a [key seq-of-values] pair by calling read-string on the
  string representations of the Writable key and values."
  [wkey wvalues]
  [(read-string (.toString wkey))
   (map #(read-string (.toString %)) (non-caching-iterator-seq wvalues))])

(defn clojure-writer
  "Sends key and value to the OutputCollector by calling pr-str on key
  and value and wrapping them in Hadoop Text objects."
  [output key value]
  (binding [*print-dup* true]
    (.collect output (Text. (pr-str key)) (Text. (pr-str value)))))

(defn wrap-map
  "Returns a function implementing the Mapper.map interface.

  f is a function of two arguments, key and value.

  f must return a *sequence* of *pairs* like 
    [[key1 value1] [key2 value2] ...]

  When f is called, *reporter* is bound to the Hadoop Reporter.

  reader is a function that receives the Writable key and value from
  Hadoop and returns a [key value] pair for f.

  writer is a function that receives each [key value] pair returned by
  f and sends the appropriately-type arguments to the Hadoop
  OutputCollector.

  If not given, reader and writer default to clojure-map-reader and
  clojure-writer, respectively."
  ([f] (wrap-map f clojure-map-reader clojure-writer))
  ([f reader] (wrap-map f reader clojure-writer))
  ([f reader writer]
     (fn [this wkey wvalue output reporter]
       (binding [*reporter* reporter]
         (doseq [pair (apply f (reader wkey wvalue))]
           (apply writer output pair))))))

(defn wrap-reduce
  "Returns a function implementing the Reducer.reduce interface.

  f is a function of two arguments, key and sequence-of-values.

  f must return a *sequence* of *pairs* like 
    [[key1 value1] [key2 value2] ...]

  When f is called, *reporter* is bound to the Hadoop Reporter.

  reader is a function that receives the Writable key and value from
  Hadoop and returns a [key seq-of-values] pair for f.

  writer is a function that receives each [key value] pair returned by
  f and sends the appropriately-type arguments to the Hadoop
  OutputCollector.

  If not given, reader and writer default to clojure-reduce-reader and
  clojure-writer, respectively."
  ([f] (wrap-reduce f clojure-reduce-reader clojure-writer))
  ([f writer] (wrap-reduce f clojure-reduce-reader writer))
  ([f reader writer]
     (fn [this wkey wvalues output reporter]
       (binding [*reporter* reporter]
         (doseq [pair (apply f (reader wkey wvalues))]
           (apply writer output pair))))))
