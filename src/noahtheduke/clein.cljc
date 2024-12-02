#!/usr/bin/env bb

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(ns ^:no-doc noahtheduke.clein
  (:require
   [babashka.process :refer [shell]]
   [clojure.java.io :as io]
   #_:clj-kondo/ignore
   [clojure.pprint :refer [pprint]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]
   [noahtheduke.pom-data :refer [write-pom]])
  (:import
   [java.lang System]))

#?(:bb (do (require '[babashka.deps :as deps])
           (deps/add-deps '{:deps {io.github.babashka/tools.bbuild
                                   {:git/sha "f5a4acaf25ec2bc5582853758ba81383fff5e86b"}}}))
   :clj (require '[babashka.process.pprint]))

(require '[clojure.tools.build.api :as b])

;; required
(s/def ::lib qualified-symbol?)
(s/def ::main simple-symbol?)
(s/def ::url string?)

(s/def :v/string string?)
(s/def :v/file #(when (string? %)
                  (let [f (io/file %)]
                    (and (.exists f) (.isFile f)))))
(s/def ::version (s/or :f :v/file :s :v/string))

(s/def :l/name string?)
(s/def :l/url string?)
(s/def :l/distribution #{:repo :manual})
(s/def :l/comments string?)
(s/def ::license (s/keys :req-un [:l/name :l/url]
                         :opt-un [:l/distribution :l/comments]))
(s/def ::pom-data vector?)

;; optional
(s/def ::jar-name string?)
(s/def ::uberjar-name string?)
(s/def ::src-dirs (s/coll-of string? :into []))
(s/def ::java-src-dirs (s/coll-of string? :into []))
(s/def ::javac-opts (s/coll-of string? :into []))
(s/def ::target-dir string?)

(s/def :scm/url string?)
(s/def :scm/connection string?)
(s/def :scm/developerConnection string?)
(s/def :scm/tag string?)
(s/def ::scm (s/keys :req-un [:scm/url :scm/tag]
                     :opt-un [:scm/connection :scm/developerConnection]))

(s/def ::build-opts (s/keys :req-un [::lib ::main ::version ::url]
                            :opt-un [::license ::pom-data
                                     ::jar-name ::uberjar-name
                                     ::src-dirs ::java-src-dirs
                                     ::javac-opts ::target-dir ::scm]))

(defn build-pom-data [opts]
  (assert (or (contains? opts :license)
              (contains? opts :pom-data))
          "Must specify ONE of :license OR :pom-data")
  (assert (not (and (contains? opts :license)
                    (contains? opts :pom-data)))
          "Must only specify :license OR :pom-data")
  (or (:pom-data opts)
      (let [license (:license opts)]
        [[:licenses
          [:license
           [:name (:name license)]
           [:url (:url license)]
           (when (:distribution license)
             [:distribution (name (:distribution license))])
           (when (:comments license)
             [:comments (:comments license)])]]])))

(defn clein-build-opts [options]
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
        (assoc $ :basis (b/create-basis {:project "deps.edn"}))
        (assoc $ :provided (b/create-basis {:project "deps.edn"
                                            :aliases [:provided]}))
        (update $ :version #(str (if (= :s (key %))
                                   (str/replace (val %) "{{git-count-revs}}" (b/git-count-revs nil))
                                   (str/trim (slurp (val %))))
                                 (when (:snapshot options)
                                   "-SNAPSHOT")))
        (update $ :src-dirs #(or (not-empty %) (:paths (:basis $))))
        (update $ :resource-dirs not-empty)
        (update $ :java-src-dirs not-empty)
        (update $ :javac-options not-empty)
        (update $ :target-dir #(or % "target"))
        (assoc $ :class-dir (str (io/file (:target-dir $) "classes")))
        (update $ :scm #(merge {:url (:url $)
                                :tag (str "v" (:version $))} %))
        (assoc $ :src-pom nil)
        (assoc $ :pom-data (build-pom-data $))
        (update $ :jar-name #(or % (format "%s-%s.jar" (name (:lib $)) (:version $))))
        (assoc $ :jar-file (str (io/file (:target-dir $) (:jar-name $))))
        (update $ :uberjar-name #(or % (format "%s-%s-standalone.jar" (name (:lib $)) (:version $))))
        (assoc $ :uber-file (str (io/file (:target-dir $) (:uberjar-name $))))))))

(defn clean [opts]
  (b/delete {:path (:class-dir opts)}))

(defn copy-src [opts]
  (b/copy-dir {:src-dirs (:src-dirs opts)
               :target-dir (:class-dir opts)}))

(defn compile-java [opts]
  (when (:java-src-dirs opts)
    (println "Compiling" (str/join ", " (:java-src-dirs opts)))
    (b/javac {:src-dirs (:java-src-dirs opts)
              :class-dir (:class-dir opts)
              :basis (:basis opts)
              :javac-opts (:javac-opts opts)})))

(defn create-jar [opts]
  (clean opts)
  (copy-src opts)
  (compile-java opts)
  (b/jar opts)
  (println "Created" (str (.getAbsoluteFile (io/file (:jar-file opts))))))

(defn create-uberjar [opts]
  (clean opts)
  (copy-src opts)
  (println "Compiling" (:lib opts))
  (compile-java opts)
  (b/compile-clj opts)
  (write-pom opts)
  (b/uber opts)
  (println "Created" (str (.getAbsoluteFile (io/file (:uber-file opts))))))

(defn deploy [opts]
  (clean opts)
  (copy-src opts)
  (compile-java opts)
  (write-pom opts)
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

(defn install [opts]
  (clean opts)
  (copy-src opts)
  (compile-java opts)
  (b/jar opts)
  (b/install opts)
  (println "Installed" (:jar-name opts)))

(def cli-options
  [[nil "--snapshot" "Append -SNAPSHOT to the version"]
   ["-h" "--help" "Shows this help"]])

(def cli-subcommands
  [["clean" "Clean the target directory"
    :action clean
    :opts [["-h" "--help" "Show this help"]]]
   ["pom" "Create just the pom.xml"
    :action write-pom
    :opts cli-options]
   ["jar" "Build the jar"
    :action create-jar
    :opts cli-options]
   ["uberjar" "Built the uberjar"
    :action create-uberjar
    :opts cli-options]
   ["deploy" "Build and deploy jar to Clojars"
    :action deploy
    :opts cli-options]
   ["install" "Build and install jar to local Maven repo"
    :action install
    :opts cli-options]])

(defn make-cmd-summary-part
  [spec]
  [(:name spec) (or (:desc spec) "")])

(defn summarize-subcommands
  [specs]
  (if (seq specs)
    (let [parts (map make-cmd-summary-part specs)
          lens (apply map (fn [& cols] (apply max (map count cols))) parts)
          lines (cli/format-lines lens parts)]
      (str/join \newline lines))
    ""))

(defn- compile-subcommand-specs
  "Modified from clojure.tools.cli/compile-option-specs"
  [arguments]
  {:post [(every? :id %)
          (every? (comp seq :name) %)
          (every? (some-fn :action :opts) %)
          (apply distinct? (or (seq (map :id %)) [true]))]}
  (map (fn [spec]
         (let [sopt-lopt-desc (take-while string? spec)
               spec-map (apply hash-map (drop (count sopt-lopt-desc) spec))
               [opt-name desc] sopt-lopt-desc]
           (merge {:id (keyword opt-name)
                   :name opt-name
                   :desc desc}
                  spec-map)))
       arguments))

(defn parse-opts [args specs & {:keys [strict in-order]
                                :or {strict true
                                     in-order true}}]
  (set/rename-keys
   (cli/parse-opts args specs
                   :strict strict
                   :in-order in-order
                   :summary-fn identity)
   {:summary :specs}))

(defn verify-cmd
  [specs [cmd-arg & args]]
  (if-let [cmd (when (and (string? cmd-arg) (not (str/starts-with? cmd-arg "-")))
                 (some #(when (= cmd-arg (:name %)) %) specs))]
    [(merge cmd (parse-opts args (:opts cmd) {:in-order false})) nil]
    [nil [(str "Unknown command: " (pr-str cmd-arg))]]))

(defn parse-subcommand
  "Inspired by clojure.tools.cli/parse-opts"
  [arguments subcommand-specs]
  (let [specs (compile-subcommand-specs subcommand-specs)
        [cmd errors] (verify-cmd specs arguments)]
    {:name (:name cmd)
     :action (:action cmd)
     :options (:options cmd)
     :args (:arguments cmd)
     :specs (:specs cmd)
     :all-specs specs
     :errors (concat errors (:errors cmd))}))

(def clein-version (delay (str/trim (slurp (io/resource "CLEIN_VERSION")))))

(defn base-help-message
  [specs cmd-specs]
  (let [lines [(str "clein v" @clein-version)
               "Usage: clein [options] command [args...]"
               ""
               "Options:"
               (cli/summarize specs)
               ""
               "Commands:"
               (summarize-subcommands cmd-specs)
               ""]]
    {:exit-message (str/join \newline lines)
     :ok true}))

(defn cmd-help-message
  [command]
  (let [lines [(str "clein " (:name command) " [options] [args...]")
               ""
               "Options:"
               (cli/summarize (:specs command))
               ""]]
    {:exit-message (str/join \newline lines)
     :ok true}))

(defn print-errors
  [errors]
  {:exit-message (str/join \newline (cons "clein errors:" errors))
   :ok false})

(defn validate-args
  [arguments base-specs command-specs]
  (let [{:keys [options errors arguments specs] :as opts}
        (parse-opts arguments base-specs)
        command (parse-subcommand arguments command-specs)
        errors (seq (concat errors (:errors command)))]
    (cond
      (:help options) (base-help-message specs (:all-specs command))
      (:help (:options command)) (cmd-help-message command)
      errors (print-errors errors)
      (:name command)
      {:command (:action command)
       :ok true
       :options (merge options (:options command))
       :args (:args command)}
      :else (base-help-message specs (:all-specs command)))))

(defn -main [& args]
  (let [{:keys [command exit-message ok options]} (validate-args args cli-options cli-subcommands)
        build-opts (clein-build-opts options)]
    (cond
      exit-message (println exit-message)
      command (command build-opts))
    (System/exit (if ok 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
