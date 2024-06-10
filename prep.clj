(ns prep
  (:require
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(def class-dir "target/classes")
(def basis (delay (b/create-basis {:project "deps.edn"})))
(def lib 'io.github.noahtheduke/clein)
(def version (-> (slurp "./resources/CLEIN_VERSION")
                 (str/trim)
                 (str "-SNAPSHOT")))

(defn clean [opts]
  (println "Cleaning target")
  (b/delete {:path "target"})
  opts)

(defn make-opts [opts]
  (assoc opts
    :lib lib
    :main 'lazytest.main
    :version version
    :basis @basis
    :scm {:tag (str "v" version)}
    :jar-file (format "target/%s-%s.jar" (name lib) version)
    :class-dir class-dir
    :src-dirs ["src"]
    :resource-dirs ["resources"]))

(defn install
  "Install built jar to local maven repo"
  [opts]
  (let [opts (make-opts opts)]
    (b/write-pom opts)
    (b/copy-dir {:src-dirs (concat (:src-dirs opts)
                                   (:resource-dirs opts))
                 :target-dir class-dir})
    (b/jar opts)
    (b/install opts)
    (println "Installed version" lib version)))
