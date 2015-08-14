(defproject org.commos/delta "0.3.1"
  :description "Communicate changes of compound values"
  :url "http://github.com/commos/delta"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.commos/shared "0.1.0"]]
  :source-paths ["src/cljc"]
  :profiles {:dev {:dependencies [[org.clojure/clojurescript "1.7.48"]]
                   :plugins [[lein-cljsbuild "1.0.6"]]
                   :test-paths ["test/cljc"]
                   :cljsbuild
                   {:builds [{:id "test"
                              :source-paths ["test/cljs"
                                             "test/cljc"]
                              :compiler {:output-to "target/js/test.js"
                                         :output-dir "target/js"
                                         :optimizations :none
                                         :target :nodejs
                                         :cache-analysis true}}]}}})
