#!/usr/bin/env bb

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.clein
  (:require
   [babashka.process :refer [shell]]
   [babashka.process.pprint]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]) 
  (:import
    [java.lang System]))

#?(:bb (do (require '[babashka.deps :as deps])
           (deps/add-deps '{:deps {io.github.babashka/tools.bbuild
                                   {:git/sha "f5a4acaf25ec2bc5582853758ba81383fff5e86b"}}})))

(require '[clojure.tools.build.api :as b])

(def cli-options
  [["-h" "--help" "Shows this help"]])

(defn help-message
  [specs]
  (let [lines ["clein v1.0"
               ""
               "Usage: splint [options] action"
               ""
               "Options:"
               (cli/summarize specs)
               ""
               "Actions:"
               "  clean    Clean the target directory"
               "  jar      Build the jar"
               "  uberjar  Build the uberjar"
               "  deploy   Deploy to Clojars"
               ; "  repl     Open a repl"
               ""]]
    {:exit-message (str/join \newline lines)
     :ok true}))

(defn print-errors
  [errors]
  {:exit-message (str/join \newline (cons "clein errors:" errors))
   :ok false})

(defn validate-opts
  [args]
  (let [{:keys [arguments options errors summary]}
        (cli/parse-opts args cli-options :strict true :summary-fn identity)]
    (cond
      (:help options) (help-message summary)
      errors (print-errors errors)
      (and (= 1 (count arguments))
           (#{"jar" "uberjar" "repl" "deploy"} (first arguments)))
      {:action (first arguments)
       :ok true
       :options options}
      :else
      (help-message summary))))

;; required
(s/def ::lib qualified-symbol?)

(s/def :v/string string?)
(s/def :v/file #(when (string? %)
                  (let [f (io/file %)]
                    (and (.exists f) (.isFile f)))))
(s/def ::version (s/or :f :v/file :s :v/string))
(s/def ::url string?)

(s/def :l/name string?)
(s/def :l/url string?)
(s/def :l/distribution #{:repo :manual})
(s/def :l/comments string?)
(s/def :l/license (s/keys :req-un [:l/name :l/url]
                           :opt-un [:l/distribution :l/comments]))
(s/def :l/licenses (s/coll-of :l/license))
(s/def ::license (s/or :license :l/license
                       :licenses :l/licenses))

;; optional
(s/def ::main simple-symbol?)
(s/def ::jar-name string?)
(s/def ::uberjar-name string?)
(s/def ::target-path string?)
(s/def ::src-paths (s/coll-of string? :into []))

(s/def :scm/url string?)
(s/def :scm/connection string?)
(s/def :scm/developerConnection string?)
(s/def :scm/tag string?)
(s/def ::scm (s/keys :req-un [:scm/url :scm/tag]
                     :opt-un [:scm/connection :scm/developerConnection]))

(s/def ::build-opts (s/keys :req-un [::lib ::license ::version ::url]
                            :opt-un [::main ::jar-name ::uberjar-name
                                     ::target-path ::src-paths ::scm]))

(defn build-pom-data [opts]
  [(->> (for [license (:license opts)]
         [:license
          [:name (:name license)]
          [:url (:url license)]
          (when (:distribution license)
            [:distribution (name (:distribution license))])
          (when (:comments license)
            [:comments (:comments license)])])
       (into [:licenses]))])

(defn clein-build-opts []
  (let [build-opts (:argmap (b/create-basis {:aliases [:clein/build]}))
        conformed (s/conform ::build-opts build-opts)]
    (cond
      (not build-opts)
      (do (println "deps.edn alias :clein/build must exist")
          (System/exit 1))
      (= ::s/invalid conformed)
      (do (println "Error in the :clein/build map:")
          (println (s/explain-str ::build-opts build-opts))
          (System/exit 1))
      :else
      (as-> conformed $
        ; conform
        (update $ :license #(if (= :license (key %)) [(val %)] (val %)))
        (update $ :version #(if (= :s (key %)) (val %) (str/trim (slurp (val %)))))
        ; defaults
        (assoc $ :basis (b/create-basis {:project "deps.edn"}))
        (update $ :src-paths #(or (not-empty %) (:paths (:basis $))))
        (update $ :target-path #(or % "target"))
        (assoc $ :class-dir (str (io/file (:target-path $) "classes")))
        (update $ :scm #(merge {:url (:url $)
                                :tag (:version $)} %))
        (assoc $ :src-pom nil)
        (assoc $ :pom-data (build-pom-data $))
        (update $ :jar-name #(or % (format "%s-%s.jar" (name (:lib $)) (:version $))))
        (assoc $ :jar-file (str (io/file (:target-path $) (:jar-name $))))
        (update $ :uberjar-name #(or % (format "%s-%s-standalone.jar" (name (:lib $)) (:version $))))
        (assoc $ :uber-file (str (io/file (:target-path $) (:uberjar-name $))))
        (assoc $ :basis (b/create-basis {}))))))

(defn clean [opts]
  (b/delete {:path (:class-dir opts)}))

(defn copy-src [opts]
  (b/copy-dir {:src-dirs (:src-paths opts)
               :target-dir (:class-dir opts)}))

(defn create-jar [opts]
  (clean opts)
  (copy-src opts)
  (b/jar opts)
  (println "Created" (str (.getAbsoluteFile (io/file (:jar-file opts))))))

(defn create-uberjar [opts]
  (when-not (:main opts)
    (println "Must specify a :main to build an uberjar")
    (System/exit 1))
  (clean opts)
  (copy-src opts)
  (println "Compiling" (:lib opts))
  (b/compile-clj opts)
  (b/write-pom opts)
  (b/uber opts)
  (println "Created" (str (.getAbsoluteFile (io/file (:uber-file opts))))))

(defn deploy [opts]
  (clean opts)
  (b/write-pom opts)
  (b/jar opts)
  (let [deploy-alias
        {:aliases
         {:deploy
          {:deps {'slipset/deps-deploy {:mvn/version "0.2.1"}}
           :exec-fn 'deps-deploy.deps-deploy/deploy
           :exec-args {:installer :remote
                       :artifact (str (b/resolve-path (:jar-file opts)))
                       :pom-file (str (b/pom-path opts))
                       :username (System/getenv "CLOJARS_USERNAME")
                       :password (System/getenv "CLOJARS_PASSWORD")}}}}
        deps-str (binding [*print-namespace-maps* false]
                   (pr-str deploy-alias))]
    (try (shell "clojure" "-Sdeps" deps-str "-X:deploy")
         (catch clojure.lang.ExceptionInfo _
           (System/exit 1)))))

(defn -main [& args]
  (let [build-opts (clein-build-opts)
        {:keys [action exit-message ok]} (validate-opts args)]
    (when exit-message
      (println exit-message))
    (case action
      "clean" (clean build-opts)
      "jar" (create-jar build-opts)
      "uberjar" (create-uberjar build-opts)
      "deploy" (deploy build-opts)
      #_:else nil)
    (System/exit (if ok 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
