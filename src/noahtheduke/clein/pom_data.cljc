; Adapted from https://github.com/matterandvoid-space/eql/blob/560db80ce487a9507a15b44387c18413d7bcc91c/build.clj
; Licensed under MIT

(ns noahtheduke.clein.pom-data
  (:require
   [clojure.data.xml :as xml]
   [clojure.java.io :as io]
   [clojure.tools.build.api :as b]))

(defn mk-dep [g a v]
  (let [pom-ns "http://maven.apache.org/POM/4.0.0"]
    (xml/sexp-as-element
      [(xml/qname pom-ns "dependency")
       [(xml/qname pom-ns "groupId") g]
       [(xml/qname pom-ns "artifactId") a]
       [(xml/qname pom-ns "version") v]
       [(xml/qname pom-ns "scope") "provided"]])))

(defn get-provided-deps [basis]
  (let [deps-map (-> basis :aliases :provided :extra-deps)]
    (mapv (fn [[k v]] (mk-dep (namespace k) (name k) (:mvn/version v)))
      deps-map)))

(defn read-xml-file [file]
  (with-open [rdr (io/reader file)]
    (xml/parse rdr {:skip-whitespace true})))

(defn write-xml-file [file xml-data]
  (with-open [wrtr (io/writer file)]
    (xml/indent xml-data wrtr)))

(defn find-and-update-dependencies [content deps]
  (map (fn [elem]
         (if (= (name (or (:tag elem) "")) "dependencies")
           (update elem :content (comp vec concat) deps)
           elem))
    content))

(defn insert-dependencies [pom-file deps]
  (let [pom-data (read-xml-file pom-file)
        updated-content (find-and-update-dependencies (:content pom-data) deps)
        updated-pom-data (assoc pom-data :content updated-content)]
    updated-pom-data))

(defn write-pom [opts]
  (b/write-pom opts)
  (let [pom-path (:pom-path opts)
        provided-deps (get-provided-deps (:provided opts))
        updated-pom (insert-dependencies pom-path provided-deps)]
    (write-xml-file pom-path updated-pom)
    nil))
