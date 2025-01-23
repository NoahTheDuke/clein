default:
    @just --list

@repl arg="":
    clojure -M{{arg}}:repl

@run *args:
    clojure -M:run {{args}}

today := `date +%F`
current_version := `cat resources/CLEIN_VERSION | xargs`

# Set version, change all instances of <<next>> to version
@set-version version:
    echo '{{version}}' > resources/CLEIN_VERSION
    fd '.(clj|edn|md)' . -x sd '<<next>>' '{{version}}' {}
    sd '{{current_version}}' '{{version}}' README.md
    sd '## Unreleased' '## Unreleased\n\n## {{version}}\n\nReleased on {{today}}.' CHANGELOG.md

@clojars:
    env CLOJARS_USERNAME='noahtheduke' CLOJARS_PASSWORD=`cat ../clojars.txt` clojure -M:run deploy
