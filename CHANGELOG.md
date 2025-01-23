# Change Log

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## Unreleased

## 0.4.0 - 2025-01-22

- Add `export` command to create a `build.clj` file that mirrors the `:clein/build` config.
- Support `{{git-count-revs}}` in version file.
- Relax `:pom-data` to be usable alongside `:license`, as long as it doesn't define `[:licenses]`.
- Note the Leiningen keys in README where there's a difference.
- Internal reorganization.
- Add `noahtheduke.clein.cli` as cli extension namespace. (Consider moving this to a separate library.)

## 0.3.1

Released on 2024-12-02.

- Stop loading `pprint`.
- Split namespaces for ease of development.
- Maybe fix analysis issue for cljdoc.org.

## 0.3.0

Released on 2024-12-02.

- Add `install` command to install in local Maven repo.
- Add `pom` command to generate `pom.xml` file.
- Support `:resource-dirs`.
- Document that `:src-dirs` and `:resource-dirs` is strongly encouraged.
- Write a little subcommand parser, might pull it out for inclusion in `tools.cli`.

## 0.2.2

- `--snapshot` allows for building and deploying a jar with version `-SNAPSHOT`.
- Include `:provided` alias.

## 0.2.0

Still very rough, maaaaaaybe don't use lol.

- Added `{{git-count-revs}}` as a template string for use in `:version`. (See [#1](https://github.com/NoahTheDuke/clein/issues/1))
- Changed `:src-paths` to `:src-dirs` to align with tools.build.
- Changed `:target-path` to `:target-dir` to match `:src-paths`.
- Added `:pom-data` as exclusive alternative to `:license`. Remove support for multiple licenses in `:license`.
- Added java compilation support in `:java-src-dirs` and `:javac-options`, which are passed to `b/javac` before compiling jars and uberjars.
- Changed `:scm` default `:tag` to prepend `"v"`. Still unsure how to expose templating here. Much to consider.

## 0.0.1

Barebones initial version. Very rough, do not use.
