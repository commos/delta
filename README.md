# commos.delta

`[org.commos/delta "0.3.0"]` provides opinionated facilities for creating, analyzing and applying directed deltas to nested EDN data structures.

The commos.delta format is designed for streaming EDN data from one service to another, leveraging EDN conveyance libraries like transit or fressian.  Further design goals were:

- Analyzable

  Validation code or change listeners are standard usecases.  If you want to react to changes in a streamed data structure, you don't want to diff if you have deltas already.

- Transformable

  It should be possible to transform deltas so that changes are reflected consistently in their sum.  A simple example of this is the `nest` transducer, which changes the hierarchical level a deltas assertion is targeted at.  But it goes as far as mixing delta streams from multiple services into one delta stream.  See delta.compscribe.

- Nested removal only in maps and sets

  For sequential things, commos.delta can exclusively assert addition à la `into` or full replacement.  The reason is that efficient deltas to ordered collections are not convenient to describe, analyze or transform.  Also, most usecases for those that lay beyond `into` are either better implemented with maps or sets or should not be implemented with commos.delta.  Note that you can always inject vectors, ordered sets or maps into a stream of commos deltas.

- Implicit deltas at your option

  Deltas can assert dissociation (a la dissoc) and up to full value replacement.  This is a useful feature to save local memory or bandwith but leaves listener services with less information.  At your option, commos.delta does the necessary extra diffing to restore implicit deltas on the receiving end.
  

commos.delta is compatible with Clojure (v. `1.7.0-RC2`) and ClojureScript (v. `0.0-3308`).

## Deltas

All three raw delta types follow this structure

```clojure
[op ks diff]
```

Meaing: Perform `op` with `diff` at target, found in `ks`.

| `op`  | Description           | Target type                 | `diff`    | Example           |
|:------|:----------------------|:----------------------------|:----------|:------------------|
| `:is` | Replace               | -                           | any value | `[:is [:foo] 42]` |
| `:in` | Put into              | Set or vector (default set) | seqable   | `[:in :foo [42]]` |
| `:ex` | Disjoin or dissociate | Set or map                  | seqable   | `[:ex :foo [42]]` |

An additional `:batch` op exists to group multiple deltas for one-pass addition.

```clojure
[:batch & deltas]
```

It may not be nested.

## Short form and pseudo deltas

The `create` function and `raw-form` transducer make it convenient to create deltas both literally and programmatically. The following short-form is supported with it:

```clojure
[op ks? v]
```

Where an empty vector is inserted in the middle and `v` is wrapped in a vector if op requires it and it isn't one already.

It also supports the `:on` and `:off` pseudo deltas which can be thought of as a deep merge for nested maps and sets.

```clojure
[(:on | :off) ks? m]
```

Where map m is transformed into the respective raw deltas.

## Usage


## Testing

### Clojure

Due to Leiningens test task not supporting cljc yet, please run tests at the REPL.

### ClojureScript

Currently, the only supported target is NodeJS.

Run `script/test` from the project root.

## License

Copyright © 2015 Leon Grapenthin

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
