(ns kotoba.lang.ipfs
  "Kubo (IPFS) HTTP API pin/fetch/nodeInfo helpers.

  CLJC port of this repo's original @etzhayyim/ipfs TypeScript package
  (the npm-published dist/ output that etzhayyim-sdk's re-export shim and
  kotoba-lang/checkpointer still depend on as a Node/TS git dependency --
  see ../../../src/ipfs.ts, kept as the npm-consumable compiled artifact).
  This namespace is the canonical Clojure/babashka/JVM implementation
  going forward.

  JVM/babashka: babashka.http-client (multipart POST) + cheshire (JSON).
  Synchronous -- returns plain values, not futures/promises.
  CLJS: browser fetch/Blob/FormData, mirroring the original TS 1:1.
  Returns a js/Promise, matching every original TS function being async.

  No third-party deps beyond babashka.http-client/cheshire on the JVM
  side; zero deps on the CLJS side (global fetch/Blob/FormData only).
  Zero etzhayyim-specific coupling -- every URL is a caller-supplied
  parameter."
  (:require #?(:clj [babashka.http-client :as http])
            #?(:clj [cheshire.core :as json])
            [clojure.string :as str]))

(defn- strip-trailing-slashes [s]
  (str/replace s #"/+$" ""))

#?(:clj
   (defn pin-blob
     "Pin `content` (String or byte[]) to Kubo at `api-url`.
     Returns {:cid \"...\" :size <int>}."
     [api-url content]
     (let [url (str (strip-trailing-slashes api-url) "/api/v0/add?pin=true&cid-version=1")
           resp (http/post url {:multipart [{:name "file" :content content}]})]
       (when-not (<= 200 (:status resp) 299)
         (throw (ex-info (str "[kotoba.lang.ipfs] pin failed: " (:status resp) " " (:body resp))
                          {:status (:status resp) :body (:body resp)})))
       ;; Kubo returns NDJSON, one line per file; the last line is the root.
       (let [lines (->> (str/split-lines (str/trim (:body resp))) (remove str/blank?))
             last-line (json/parse-string (last lines) true)]
         {:cid (:Hash last-line)
          :size (Long/parseLong (str (:Size last-line)))}))))

#?(:cljs
   (defn pin-blob
     "Pin `blob` (js/Blob, js/Uint8Array, or string) to Kubo at `api-url`.
     Returns a js/Promise of {:cid \"...\" :size <int>}."
     [api-url blob]
     (let [body (cond
                  (string? blob) (js/Blob. #js [blob] #js {:type "application/octet-stream"})
                  (instance? js/Blob blob) blob
                  :else (js/Blob. #js [blob] #js {:type "application/octet-stream"}))
           form (js/FormData.)
           _ (.append form "file" body)
           url (str (strip-trailing-slashes api-url) "/api/v0/add?pin=true&cid-version=1")]
       (-> (js/fetch url #js {:method "POST" :body form})
           (.then (fn [res]
                    (if-not (.-ok res)
                      (.then (.text res)
                             (fn [t] (throw (js/Error. (str "[kotoba.lang.ipfs] pin failed: " (.-status res) " " t)))))
                      (.text res))))
           (.then (fn [text]
                    (let [lines (->> (str/split-lines (str/trim text)) (remove str/blank?))
                          last-line (js->clj (js/JSON.parse (last lines)) :keywordize-keys true)]
                      {:cid (:Hash last-line)
                       :size (js/Number (:Size last-line))})))))))

#?(:clj
   (defn fetch-blob
     "Fetch a blob from Kubo gateway `gateway-url` by `cid`. Returns a
     byte[]."
     [gateway-url cid]
     (let [url (str (strip-trailing-slashes gateway-url) "/ipfs/" cid)
           resp (http/get url {:as :bytes})]
       (when-not (<= 200 (:status resp) 299)
         (throw (ex-info (str "[kotoba.lang.ipfs] fetch failed: " (:status resp))
                          {:status (:status resp)})))
       (:body resp))))

#?(:cljs
   (defn fetch-blob
     "Fetch a blob from Kubo gateway `gateway-url` by `cid`. Returns a
     js/Promise of a js/Blob."
     [gateway-url cid]
     (let [url (str (strip-trailing-slashes gateway-url) "/ipfs/" (js/encodeURIComponent cid))]
       (-> (js/fetch url)
           (.then (fn [res]
                    (if-not (.-ok res)
                      (.then (.text res)
                             (fn [t] (throw (js/Error. (str "[kotoba.lang.ipfs] fetch failed: " (.-status res) " " t)))))
                      (.blob res))))))))

#?(:clj
   (defn node-info
     "Check the connected Kubo node at `api-url` is reachable.
     Returns {:id \"...\" :version \"...\"}."
     [api-url]
     (let [base (strip-trailing-slashes api-url)
           v-resp (http/post (str base "/api/v0/version"))]
       (when-not (<= 200 (:status v-resp) 299)
         (throw (ex-info (str "[kotoba.lang.ipfs] node unreachable: " (:status v-resp))
                          {:status (:status v-resp)})))
       (let [version-data (json/parse-string (:body v-resp) true)
             id-resp (http/post (str base "/api/v0/id"))
             id-data (json/parse-string (:body id-resp) true)]
         {:id (:ID id-data)
          :version (:Version version-data)}))))

#?(:cljs
   (defn node-info
     "Check the connected Kubo node at `api-url` is reachable. Returns a
     js/Promise of {:id \"...\" :version \"...\"}."
     [api-url]
     (let [base (strip-trailing-slashes api-url)]
       (-> (js/fetch (str base "/api/v0/version") #js {:method "POST"})
           (.then (fn [res]
                    (when-not (.-ok res)
                      (throw (js/Error. (str "[kotoba.lang.ipfs] node unreachable: " (.-status res)))))
                    (.json res)))
           (.then (fn [version-data]
                    (-> (js/fetch (str base "/api/v0/id") #js {:method "POST"})
                        (.then #(.json %))
                        (.then (fn [id-data]
                                 {:id (.-ID id-data)
                                  :version (.-Version version-data)})))))))))
