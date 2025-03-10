(ns noahtheduke.clein.cli
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.tools.cli :as cli]))

(def cli-help ["-h" "--help" "Show this help"])

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

(def ^:private spec-keys
  #{:name :desc :action :opts :help})

(defn- check-spec-keys
  [spec-map]
  (when *assert*
    (when-let [remainder (not-empty (keys (apply dissoc spec-map spec-keys)))]
      (binding [*out* *err*]
        (println (format "Warning: Unknown parse-command opts '%s'"
                         (str/join ", " remainder))))))
  spec-map)

(defn- conform-spec
  [spec]
  (let [sopt-lopt-desc (take-while #(or (string? %) (nil? %)) spec)
        spec-map (apply hash-map (drop (count sopt-lopt-desc) spec))
        [opt-name desc] sopt-lopt-desc]
    (check-spec-keys spec-map)
    (merge {:name opt-name
            :desc desc}
           (cond-> spec-map
             true (dissoc :help)
             (:help spec-map true) (update :opts (fnil conj []) cli-help)))))

(defn- compile-subcommand-specs
  "Modified from clojure.tools.cli/compile-option-specs"
  [arguments]
  {:post [(every? (fn [{n :name}]
                    (and (string? n)
                         (not (str/blank? n))
                         (not (str/starts-with? n "-")))) %)
          (every? (comp ifn? :action) %)
          (apply distinct? (or (seq (map :name %)) [true]))]}
  (map (fn [spec]
         (if (map? spec)
           (check-spec-keys spec)
           (conform-spec spec)))
       arguments))

(defn parse-opts
  [args specs & {:keys [strict in-order]
                 :or {strict true
                      in-order true}}]
  (set/rename-keys
   (cli/parse-opts args specs
                   :strict strict
                   :in-order in-order
                   :summary-fn identity)
   {:summary :specs}))

(defn parse-command
  "Inspired by clojure.tools.cli/parse-opts.

  Parses a command and optional trailing arguments into a map of the command,
  the action function, and command-specific options/arguments, along with any
  errors and all of the command specs for use in printing later.

  Given command specification:

  [\"clean\" \"Clean the target directory.\"
   :action #'clean-target
   :opts [[\"-p\" \"--path PATH\" \"Which directory to clean.\"]]]

  and arguments:

  [\"clean\" \"--path\" \"pom.xml\"]

  [[parse-subcommand]] will return:

  {:name \"clean\"
   :action #'clean-target
   :options {:path \"pom.xml\"}
   :}

  Command specifications are a sequence of vectors with the following format:

    [name description
     :property value]

  The first two string parameters in a command spec are positional and optional,
  and may be nil in order to specify a later parameter. The command name must
  not begin with a '-' to avoid clashing with flags.

  The :property value pairs are mostly optional and take precedence over the
  positional string arguments. The valid properties are:

    :name    The name of the command. Must not begin with a '-' to avoid
             clashing with flags. If a string command name parameter is not
             given, then this property is mandatory.

    :desc    An short description of this command. If a string description
             parameter is not given, then this property is mandatory.

    :action  The IFn that will be executed if the command is chosen. This
             property is mandatory.

    :opts    `clojure.tools.cli` option spec that will be used if the command is
             chosen. This property is optional.

             [\"clean\" \"Clean the target directory.\"
              :action #'clean-target
              :opts [[\"-p\" \"--path PATH\" \"Which directory to clean.\"]]]

    :help    Boolean, defaults to true. If logical true, then `:opts` will be
             appended with a `-h/--help` option spec.

  If desired, the :property value pairs can be a map literal:

    [name description
     {:property value}]
  "
  [[cmd-arg & args] subcommand-specs]
  (let [specs (compile-subcommand-specs subcommand-specs)]
    (if-let [cmd (when (and (string? cmd-arg) (not (str/starts-with? cmd-arg "-")))
                   (some #(when (= cmd-arg (:name %)) %) specs))]
      (merge cmd
             (parse-opts args (:opts cmd) {:in-order false})
             {:all-specs specs})
      {:errors [(str "Unknown command: " (pr-str cmd-arg))]
       :all-specs specs})))

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
  (let [opts (parse-opts arguments base-specs)
        command (parse-command (:arguments opts) command-specs)
        errors (or (:errors opts) (:errors command))]
    (cond
      (:help (:options opts)) (base-help-message (:specs opts) (:all-specs command))
      (:help (:options command)) (cmd-help-message command)
      errors (print-errors errors)
      command
      {:command (:action command)
       :ok true
       :options (merge (:options opts) (:options command))
       :args (:args command)}
      :else (base-help-message (:specs opts) (:all-specs command)))))
