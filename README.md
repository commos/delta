# commos.delta

`[org.commos/delta "0.1.2"]` is designed to help communicate changes of compound values.

It is compatible with Clojure (v. 1.7.0-alpha5) and ClojureScript.

## Usage
The `add` function is used to apply a delta to any supported value. Supported values are maps, sets, and all elements that are not collections.

```clojure
(add nil [:is 42])
;; -> 42
```

While `:is` deltas are mostly useful for communicating an initial value, `:in` deltas can be used to add one or more values to a set:

```clojure
(add nil [:in 42])
;; -> #{42}

(add nil [:in [42 43]])
;; -> #{43 42}
```

Finally, there are `:ex` deltas:

```clojure
(add #{42 43} [:ex 43])
;; -> #{42}
```
All deltas support associative nesting:
```clojure
(add nil [:in :foo 42])
;; -> {:foo #{42}}
```
Key sequences are possible, too
```clojure
(add nil [:in [:foo :bar] 42])
;; -> {:foo {:bar #{42}}}
```

`:ex` deltas can be used for dissociation:
```clojure
(add {:foo {:bar #{42}}} [:ex [:foo] :bar])
;; -> {:foo {}}
```

Accompanying `add`, the library provides utility functions and transducers. Please refer to docstrings in the `commos.delta` namespace. 

## Sequential colls

Sequential colls (lists, vectors) are not supported for manipulation via `:in` and `:ex`. Clojure implementation details could maybe be abused to achieve that, which is unsupported. If you have a usecase, please raise an issue.

## Testing

### Clojure

Run `lein cleantest`.

### ClojureScript

Currently, the only supported target is NodeJS.

Run `script/test` from the project root.

## License

Copyright © 2015 Leon Grapenthin

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
