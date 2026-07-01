(ns kotoba.lang.ipfs-test
  "JVM-only: exercises kotoba.lang.ipfs's :clj branch against a real,
  dependency-free mock Kubo HTTP server (com.sun.net.httpserver.HttpServer,
  part of the JDK -- no mock-server library needed). Plain .clj, not .cljc,
  since this test infrastructure only makes sense on the JVM; the source
  under test (kotoba.lang.ipfs) stays .cljc for CLJS/browser portability."
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [kotoba.lang.ipfs :as ipfs]
            [cheshire.core :as json])
  (:import [com.sun.net.httpserver HttpServer HttpHandler HttpExchange]
           [java.net InetSocketAddress]
           [java.io ByteArrayOutputStream]))

(defn- slurp-bytes [^java.io.InputStream is]
  (let [out (ByteArrayOutputStream.)]
    (.transferTo is out)
    (.toByteArray out)))

(defn- respond! [^HttpExchange exchange status ^String body]
  (let [bytes (.getBytes body "UTF-8")]
    (.sendResponseHeaders exchange status (count bytes))
    (with-open [os (.getResponseBody exchange)]
      (.write os bytes))))

(defn- mock-kubo-handler
  "Routes the same 3 endpoints kotoba.lang.ipfs calls, mirroring Kubo's
  real response shapes closely enough to prove the client parses them
  correctly."
  [^HttpExchange exchange]
  (let [path (.getPath (.getRequestURI exchange))]
    (cond
      (= path "/api/v0/add")
      (let [body (slurp-bytes (.getRequestBody exchange))]
        ;; Prove the multipart body actually contains our content.
        (if (clojure.string/includes? (String. body "UTF-8") "hello kotoba")
          (respond! exchange 200 "{\"Name\":\"file\",\"Hash\":\"bafyhello123\",\"Size\":\"12\"}\n")
          (respond! exchange 400 "unexpected body")))

      (clojure.string/starts-with? path "/ipfs/")
      (respond! exchange 200 "blob-content-for-bafyhello123")

      (= path "/api/v0/version")
      (respond! exchange 200 (json/generate-string {:Version "0.30.0"}))

      (= path "/api/v0/id")
      (respond! exchange 200 (json/generate-string {:ID "12D3KooWtest"}))

      :else
      (respond! exchange 404 "not found"))))

(def ^:dynamic *base-url* nil)
(def ^:private server (atom nil))

(defn- with-mock-kubo [f]
  (let [s (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext s "/" (reify HttpHandler (handle [_ ex] (mock-kubo-handler ex))))
    (.start s)
    (reset! server s)
    (try
      (binding [*base-url* (str "http://127.0.0.1:" (.getPort (.getAddress s)))]
        (f))
      (finally
        (.stop s 0)))))

(use-fixtures :each with-mock-kubo)

(deftest pin-blob-test
  (testing "pins a string blob and parses the NDJSON response"
    (let [result (ipfs/pin-blob *base-url* "hello kotoba")]
      (is (= "bafyhello123" (:cid result)))
      (is (= 12 (:size result)))))

  (testing "throws on an unexpected server response"
    (is (thrown? clojure.lang.ExceptionInfo
                 (ipfs/pin-blob *base-url* "wrong content")))))

(deftest fetch-blob-test
  (let [bytes (ipfs/fetch-blob *base-url* "bafyhello123")]
    (is (bytes? bytes))
    (is (= "blob-content-for-bafyhello123" (String. bytes "UTF-8")))))

(deftest node-info-test
  (let [info (ipfs/node-info *base-url*)]
    (is (= "12D3KooWtest" (:id info)))
    (is (= "0.30.0" (:version info)))))
