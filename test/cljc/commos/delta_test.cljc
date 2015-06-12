(ns commos.delta-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [commos.delta :refer [add nest values]]))

(deftest test-add
  (testing ":is"
    (is (= 42
           (add nil [:is 42])))
    (is (= {:foo 42}
           (add nil [:is :foo 42]))))
  (testing ":in"
    (is (= #{42}
           (add nil [:in 42])))
    (is (= #{false}
           (add nil [:in false])))
    (is (= {:foo #{42}}
           (add nil [:in :foo 42])))
    (is (= {:foo #{nil}}
           (add nil [:in [:foo] nil]))))
  (testing ":ex"
    (is (= #{}
           (add #{42} [:ex 42])))
    (is (= {}
           (add {:foo #{42}} [:ex :foo 42])))
    (is (= {}
           (add {:foo 42} [:ex :foo])))
    (is (= {:foo {:baz :quux}}
           (add {:foo {:bar 42
                       :baz :quux}} [:ex [:foo] :bar]))))
  (testing ":on"
    (is (= {:foo {:bar 42}}
           (add nil [:on {:foo {:bar 42}}])))
    (is (= {:foo {:bar #{42}}}
           (add nil [:on {:foo {:bar [42]}}])))
    (is (= {:foo {:bar #{42 43}
                  :baz :quux}}
           (add {:foo {:bar #{42}
                       :baz :quux}}
                [:on {:foo {:bar #{43}}}]))))
  (testing ":off"
    (is (= {:foo {:bar #{42}}}
           (add {:foo {:bar #{42 43}
                       :baz :quux}}
                [:off {:foo {:bar #{43}
                             :baz :quux}}])))))

(deftest test-nest
  (is (= {:foo 42} (transduce (nest [:foo]) add [[:is 42]])))
  (is (= {:foo {:bar 42}} (transduce (nest [:foo]) add [[:is :bar 42]]))))
