# Clein

Build and deploy your projects with ease! (But only if they're not too complicated.)

## How to install

### Deps.edn

In your `deps.edn`, add this library to a new alias such as `:build`:

```clojure
{:aliases
 {:build {:deps {io.github.noahtheduke/clein {:mvn/version "LATEST"}}
          :main-opts ["-m" "noahtheduke.clein"]}}
```

Run with `clojure -M:build [...]`.

### Babashka

Either copy `src/noahtheduke/clein.cljc` to a folder on your $PATH and `chmod +x` it, or
use [bbin](https://github.com/babashka/bbin) to install it.

## How to configure

Add the alias `:clein/build` with the clein-specific configuration. The example below
details all possible options with their defaults:

```clojure
{:aliases
 {...
  :clein/build
  {:lib io.github.noahtheduke/clein ; required
   :main noahtheduke.clein ; required only if building an uberjar
   :url "https://github.com/noahtheduke/clein" ; required

   ; :version is required
   ; Must be either a string, or a path string that resolves to a file:
   ; :version "resources/CLEIN_VERSION"
   :version "1.0.0"

   ; :license is required
   ; Only allows :name, :url, :distribution, :comments.
   ; :name and :url and required, :distribution and :comments are optional.
   ; If not included, defaults to :distribution :repo, no :comments.
   :license {:name "MPL-2.0"
             :url "https://mozilla.org/MPL/2.0"}

   ; :src-paths is optional
   ; If not included, defaults to :paths in deps.edn.
   ; :paths should include any "resources" paths you want as well.
   :src-paths ["src"]

   ; :target is optional
   ; If not included, defaults to "target".
   ; Classes are compiled into :target/classes, and jar and uberjar are built in
   ; :target/:jar-name and :target/:uberjar-name, respectively.
   :target-path "target"

   :jar-name "clein.jar" ; optional, default (format "%s-%s.jar" (name lib) version)
   :uberjar-name "clein-standalone.jar" ; optional, default (format "%s-%s-standalone.jar" (name lib) version)

   ; :scm is optional
   ; If not included, defaults to above :url and :version, no :connection or :developerConnection
   ; Only allows :url, :connection, :developerConnection, :tag
   :scm {:url "https://github.com/noahtheduke/clein"
         :tag "v1.0.0"}}}}
```

## Rationale

I hate copy-pasting my nearly identical build.clj files from one project to the next.
The Clojure team might be right that builds should be treated as code because otherwise
you have adhoc DSLs. But on the other hand, most projects just aren't that complicated
and can be handled with a few light decisions. Leiningen got this right, making the
simple case dead simple. Leiningen failed once projects got more complex.

Surrounded by a graveyard of other `tools.build` libraries and tools, I have decided to
try my hand. This is a very simple wrapper around tools.build that moves the
configuration from a build.clj file to a `deps.edn` alias.
