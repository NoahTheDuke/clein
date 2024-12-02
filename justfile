default:
    @just --list

run *args:
    clojure -M:run {{args}}

@clojars:
    env CLOJARS_USERNAME='noahtheduke' CLOJARS_PASSWORD=`cat ../clojars.txt` clojure -M:clein deploy
