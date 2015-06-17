(ns commos.delta-test
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [commos.delta :refer [add nest values create]]))

(deftest test-add
  (testing ":is"
    (is (= 42
           (add nil
                (create [:is 42]))))
    (is (= {:foo 42}
           (add nil
                (create [:is :foo 42])))))
  (testing ":in"
    (is (= #{42}
           (add nil
                (create [:in 42]))))
    (is (= #{false}
           (add nil
                (create [:in false]))))
    (is (= {:foo #{42}}
           (add nil
                (create [:in :foo 42]))))
    (is (= {:foo #{nil}}
           (add nil
                (create [:in [:foo] nil])))))
  (testing ":ex"
    (is (= #{}
           (add #{42}
                (create [:ex 42]))))
    (is (= {}
           (add {:foo #{42}}
                (create [:ex :foo 42]))))
    (is (= {}
           (add {:foo 42}
                (create [:ex :foo]))))
    (is (= {:foo {:baz :quux}}
           (add {:foo {:bar 42
                       :baz :quux}}
                (create [:ex [:foo] :bar])))))
  (testing ":on"
    (is (= {:foo {:bar 42}}
           (add nil
                (create [:on {:foo {:bar 42}}]))))
    (is (= {:foo {:bar #{42}}}
           (add nil
                (create [:on {:foo {:bar [42]}}]))))
    (is (= {:foo {:bar #{42 43}
                  :baz :quux}}
           (add {:foo {:bar #{42}
                       :baz :quux}}
                (create
                 [:on {:foo {:bar #{43}}}])))))
  (testing ":off"
    (is (= {:foo {:bar #{42}}}
           (add {:foo {:bar #{42 43}
                       :baz :quux}}
                (create
                 [:off {:foo {:bar #{43}
                              :baz :quux}}]))))))

(deftest test-nest
  (is (= {:foo 42}
         (transduce (nest [:foo]) add [(create [:is 42])])))
  (is (= {:foo {:bar 42}}
         (transduce (nest [:foo]) add [(create [:is :bar 42])]))))
