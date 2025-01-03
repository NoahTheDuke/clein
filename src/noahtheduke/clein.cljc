#!/usr/bin/env bb

; This Source Code Form is subject to the terms of the Mozilla Public
; License, v. 2.0. If a copy of the MPL was not distributed with this
; file, You can obtain one at https://mozilla.org/MPL/2.0/.

(in-ns 'noahtheduke.clein)

#?(:bb (do (clojure.core/require '[babashka.deps :as deps])
           (deps/add-deps '{:deps {io.github.babashka/tools.bbuild
                                   {:git/sha "f5a4acaf25ec2bc5582853758ba81383fff5e86b"}}})))

(ns ^:no-doc noahtheduke.clein
  (:require
   [babashka.process :as process]
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]
   [noahtheduke.clein.pom-data :as pom]
   [noahtheduke.clein.specs :as specs]
   [noahtheduke.clein.cli :as cli])
  (:import
   [java.lang System]))

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
        conformed (s/conform ::specs/build-opts build-opts)]
    (cond
      (not build-opts)
      (do (println "deps.edn alias :clein/build must exist")
          (System/exit 1))
      (= ::s/invalid conformed)
      (do (println "Error in the :clein/build map:")
          (println (s/explain-str ::specs/build-opts build-opts))
          (System/exit 1))
      :else
      (as-> conformed $
        (assoc $ :options options)
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
        (assoc $ :pom-path (b/pom-path $))
        (update $ :jar-name #(or % (format "%s-%s.jar" (name (:lib $)) (:version $))))
        (assoc $ :jar-file (str (io/file (:target-dir $) (:jar-name $))))
        (update $ :uberjar-name #(or % (format "%s-%s-standalone.jar" (name (:lib $)) (:version $))))
        (assoc $ :uber-file (str (io/file (:target-dir $) (:uberjar-name $))))))))

(defn clean [opts]
  (b/delete {:path (:class-dir opts)}))

(defn write-pom [opts]
  (let [target-dir (some-> opts :options :target-dir)
        pom-path (if target-dir
                   (str (io/file target-dir "pom.xml"))
                   (:pom-path opts))
        opts (assoc opts :pom-path pom-path)]
    (pom/write-pom (if target-dir
                     (-> opts
                         (dissoc :class-dir)
                         (assoc :target target-dir))
                     opts))
    (println "Wrote pom to" pom-path)))

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
  (pom/write-pom opts)
  (b/uber opts)
  (println "Created" (str (.getAbsoluteFile (io/file (:uber-file opts))))))

(defn deploy [opts]
  (clean opts)
  (copy-src opts)
  (compile-java opts)
  (pom/write-pom opts)
  (b/jar opts)
  (let [deploy-alias
        {:aliases
         {:deploy
          {:deps {'slipset/deps-deploy {:mvn/version "0.2.1"}}
           :exec-fn 'deps-deploy.deps-deploy/deploy
           :exec-args {:installer :remote
                       :artifact (str (b/resolve-path (:jar-file opts)))
                       :pom-file (str (:pom-path opts))
                       :username (System/getenv "CLOJARS_USERNAME")
                       :password (System/getenv "CLOJARS_PASSWORD")}}}}
        deps-str (binding [*print-namespace-maps* false]
                   (pr-str deploy-alias))]
    (try (process/shell "clojure" "-Sdeps" deps-str "-X:deploy")
         (catch clojure.lang.ExceptionInfo _
           (System/exit 1)))))

(defn install [opts]
  (clean opts)
  (copy-src opts)
  (compile-java opts)
  (b/jar opts)
  (b/install opts)
  (println "Installed" (:jar-name opts)))

(def ^:private base-specs [cli/cli-help])
(def ^:private cli-snapshot [nil "--snapshot" "Append -SNAPSHOT to the version"])

(def cli-subcommands
  [["clean" "Clean the target directory"
    :action clean]
   ["pom" "Create just the pom.xml"
    :action write-pom
    :opts [[nil "--target-dir dir" "Output pom.xml to DIR (defaults to target directory)"]
           cli-snapshot]]
   ["jar" "Build the jar"
    :action create-jar
    :opts [cli-snapshot]]
   ["uberjar" "Built the uberjar"
    :action create-uberjar
    :opts [cli-snapshot]]
   ["deploy" "Build and deploy jar to Clojars"
    :action deploy
    :opts [cli-snapshot]]
   ["install" "Build and install jar to local Maven repo"
    :action install
    :opts [cli-snapshot]]])

(defn -main [& args]
  (let [{:keys [command exit-message ok options]} (cli/validate-args args base-specs cli-subcommands)
        build-opts (clein-build-opts options)]
    (cond
      exit-message (println exit-message)
      command (command build-opts))
    (System/exit (if ok 0 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
