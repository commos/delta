# commos.delta

`[org.commos/delta "0.3.0"]` provides opinionated facilities for creating, analyzing and applying directed deltas to nested EDN data structures.

The commos.delta format is designed for exchanging EDN data between services.  Design goals for its deltas are

- Analyzable

  Validation code or change listeners are standard usecases.  If you want to listen to changes to a streamed data structure, you don't want to diff unless you have to.

- Transformable

  This allows, e. g. for services mixing delta streams from multiple services into one delta stream.  See delta.compscribe.

- Only maps and sets

  Deltas can only assert full replacements of vectors, lists or sequential types in general (unless you convert them into maps or sets).  The reason is that efficient deltas to ordered collections are not easy to analyze or transform.  Also, most usecases for those are either better implemented with maps or sets or should not be implemented with commos.delta.  Note that you can always inject ordered sets or maps into a stream of commos deltas.

- Implicit deltas at your option

  Deltas can assert dissociation (a la dissoc) and up to full value replacement.  This is a useful feature to save local memory or bandwith.  At your option, commos.delta does the necessary extra diffing to re-create the implicit deltas for analysis.
  

commos.delta is compatible with Clojure (v. 1.7.0-RC2) and ClojureScript (v. 0.0-3308).

## Usage


## Testing

### Clojure

Due to Leiningens test task not supporting cljc yet, please run tests from the REPL.

### ClojureScript

Currently, the only supported target is NodeJS.

Run `script/test` from the project root.

## License

Copyright © 2015 Leon Grapenthin

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
