(ns noahtheduke.clein.specs
  (:require
   [clojure.java.io :as io]
   [clojure.spec.alpha :as s]))

;; required
(s/def ::lib qualified-symbol?)
(s/def ::main simple-symbol?)
(s/def ::url string?)

(s/def :v/string string?)
(s/def :v/file #(when (string? %)
                  (let [f (io/file %)]
                    (and (.exists f) (.isFile f)))))
(s/def ::version (s/or :file :v/file :string :v/string))

;; spec'd as optional but required in assertions
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
