# Clein

Build and deploy your projects with ease! (But only if they're not too complicated.)

## How to install

### Deps.edn

In your `deps.edn`, add this library to a new alias such as `:clein`:

```clojure
{:aliases
 {:clein {:deps {io.github.noahtheduke/clein {:mvn/version "LATEST"}}
          :main-opts ["-m" "noahtheduke.clein"]}}
```

Run with `clojure -M:clein [...]`.

### Babashka

Either copy `src/noahtheduke/clein.cljc` to a folder on your $PATH and `chmod +x` it, or
use [bbin](https://github.com/babashka/bbin) to install it.

## How to use

Using the above alias:

```
$ clojure -M:clein --help
clein v0.3.1
Usage: clein [options] command [args...]

Options:
      --snapshot  Append -SNAPSHOT to the version
  -h, --help      Shows this help

Commands:
  clean    Clean the target directory
  pom      Create just the pom.xml
  jar      Build the jar
  uberjar  Built the uberjar
  deploy   Build and deploy jar to Clojars
  install  Build and install jar to local Maven repo
```

## How to configure

Add the alias `:clein/build` with the clein-specific configuration. The example below
details all possible options with their defaults:

```clojure
{:aliases
 {...
  :clein/build
  {:lib io.github.noahtheduke/clein ; required
   :main noahtheduke.clein ; required
   :url "https://github.com/noahtheduke/clein" ; required

   ; :version is required
   ; Must be either a string, or a path string that resolves to a file.
   ; If given a string, it can contain the string "{{git-count-revs}}"
   ; to use the result of `b/git-count-revs`. No other template strings work.
   :version "1.0.0"
   ; :version "1.0.{{git-count-revs}}"
   ; :version "resources/CLEIN_VERSION".

   ; :license OR :pom-data are required, but not both.

   ; :license only allows :name, :url, :distribution, :comments.
   ; :name and :url and required, :distribution and :comments are optional.
   ; If not included, defaults to :distribution :repo, no :comments.
   :license {:name "MPL-2.0"
             :url "https://mozilla.org/MPL/2.0"}

   ; :pom-data is checked to be a vector and is otherwise passed directly
   ; to b/write-pom.
   :pom-data [[:licenses
               [:license
                [:name "MPL-2.0"]
                [:url "https://mozilla.org/MPL/2.0"]
                [:distribution "repo"]]]]

   ; :src-dirs is optional but STRONGLY RECOMMENDED
   ; If not included, defaults to :paths in deps.edn.
   :src-dirs ["src/clojure"]

   ; :resource-dirs is optional but STRONGLY RECOMMENDED
   :resource-dirs ["resources"]

   ; :java-src-dirs is optional
   ; If not included, defaults to nil and no java compilation will happen.
   ; If included, the given directories will be compiled before jars are created.
   ; Java classes are compiled into :target/classes as below.
   :java-src-dirs ["src/java"]

   ; :javac-opts is optional
   ; If included, it will be passed to b/javac as-is.
   :javac-opts ["--release" "11"]

   ; :target-dir is optional
   ; If not included, defaults to "target".
   ; Classes are compiled into :target-dir/classes, and jar and uberjar are built in
   ; :target-dir/:jar-name and :target-dir/:uberjar-name, respectively.
   :target-dir "target"

   :jar-name "clein.jar" ; optional, default (format "%s-%s.jar" (name lib) version)
   :uberjar-name "clein-standalone.jar" ; optional, default (format "%s-%s-standalone.jar" (name lib) version)

   ; :scm is optional
   ; If not included, defaults to above :url and (str "v" :version), no :connection or :developerConnection
   ; Only allows :url, :connection, :developerConnection, :tag
   :scm {:url "https://github.com/noahtheduke/clein"
         :tag "v1.0.0"}}}}
```

Additionally, if there is a `:provided` alias with `:extra-deps`, it will be included in
the generated `pom.xml` with `<scope>provided</scope>` when building the uberjar, and
deploying. This allows specifying `:extra-deps` that users must include themselves,
generally for optional dependencies.

## Rationale

I hate copy-pasting my nearly identical build.clj files from one project to the next.
The Clojure team might be right that builds should be treated as code because otherwise
you have adhoc DSLs. But on the other hand, most projects just aren't that complicated
and can be handled with a few light decisions. Leiningen got this right, making the
simple case dead simple. Leiningen failed once projects got more complex.

Surrounded by a graveyard of other `tools.build` libraries and tools, I have decided to
try my hand. This is a very simple wrapper around tools.build that moves the
configuration from a build.clj file to a `deps.edn` alias.
