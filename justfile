default:
    @just --list

@repl arg="":
    clojure -M{{arg}}:repl

@run *args:
    clojure -M:run {{args}}

@clojars:
    env CLOJARS_USERNAME='noahtheduke' CLOJARS_PASSWORD=`cat ../clojars.txt` clojure -M:run deploy
