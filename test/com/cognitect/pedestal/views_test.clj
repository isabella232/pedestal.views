(ns com.cognitect.pedestal.views-test
  (:require [clojure.test :refer :all]
            [com.cognitect.pedestal.views :refer :all]
            [io.pedestal.interceptor.chain :as chain])
  (:import clojure.lang.ExceptionInfo
           java.nio.ByteBuffer))

(defn- run-interceptor
  ([i]     (run-interceptor {} i))
  ([ctx i] (chain/execute (chain/enqueue* ctx i))))

(defn- ctx-with-renderer
  [r]
  {:response {:view r}})

(defn- rendered
  [ctx]
  (:response (run-interceptor ctx renderer)))

(defn hello-fn
  [_]
  "cake")

(deftest rendering
  (are [expected renderer] (= expected (:body (rendered (ctx-with-renderer renderer))))
    "hello!" (constantly "hello!")
    "cake"   hello-fn
    "cake"   #'hello-fn
    "cake"   :com.cognitect.pedestal.views-test/hello-fn))

(deftest passthrough-when-no-view-given
  (is (= "cake" (:body (rendered {:response {:body "cake"}})))))

(deftest missing-render-fn
  (are [renderer] (thrown-with-msg? ExceptionInfo #"Missing render function"
                                    (rendered (ctx-with-renderer renderer)))
    :no.such.fn/exists
    'no.such/symbol
    (clojure.lang.Var/create)))

(deftest status-not-changed-if-set
  (is (= 202
         (:status (rendered {:response {:status 202 :view hello-fn}})))))

(defn boom [_] (throw (ex-info "Boom!" {})))

(deftest render-fn-throws
  (is (thrown-with-msg? ExceptionInfo #"Boom!" (rendered (ctx-with-renderer boom)))))

(defmacro exceptions
  [f]
  `(try
    ~f
    (assert false "exception not thrown.")
    (catch Throwable t#
      (take-while boolean (iterate #(.getCause %) t#)))))

(deftest invalid-view-selectors
  (are [renderer] (= [ExceptionInfo AssertionError]
                     (map type (exceptions (rendered (ctx-with-renderer renderer)))))
    "foobar"
    nil))

(defn- canned-text
  [msg]
  (rendered (ctx-with-renderer (constantly msg))))

(deftest content-length
  (let [message        "This is a single short message."
        response       (canned-text message)
        content-length (get-in response [:headers "Content-Length"])]
    (is (not (nil? content-length)))
    (is (= (count message) (Long/parseLong content-length)))))

(deftest content-type
  (let [message        "This is a single short message."
        response       (canned-text message)
        content-type   (get-in response [:headers "Content-Type"])]
    (is (not (nil? content-type)))
    (is (= (.startsWith content-type "text/html")))))

(defn- async-response?
  [x]
  (instance? ByteBuffer (:body x)))

(deftest long-messages-are-async
  (is (async-response? (canned-text (repeat 4096 "A")))))

(run-tests)
