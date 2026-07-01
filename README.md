# ipfs

`@etzhayyim/ipfs` — Kubo (IPFS) HTTP API pin/fetch/nodeInfo helpers.

| Export | What it does |
|---|---|
| `pinBlob(apiUrl, blob)` | POSTs a blob to Kubo's `/api/v0/add?pin=true&cid-version=1`, returns the resulting CID + size |
| `fetchBlob(gatewayUrl, cid)` | GETs a blob from a Kubo gateway's `/ipfs/<cid>` |
| `nodeInfo(apiUrl)` | Checks a Kubo node is reachable, returns its peer ID + version |

Zero npm dependencies (`fetch`/`Blob`/`FormData` only), zero etzhayyim-specific
coupling — every URL is a caller-supplied parameter.

## Provenance

Relocated 2026-07-01 from `etzhayyim/root:20-actors/etzhayyim-sdk/src/
ipfs.ts` to `kotoba-lang/ipfs` per the org-taxonomy library-placement rule
(any library/substrate code belongs in `kotoba-lang`, ADR-2606302300).
Design authority remains ADR-2605171800 Stage 4, in `etzhayyim/root`.

Two real consumers inside `etzhayyim-sdk`: `index.ts` (the `Etzhayyim`
class's blob read/write methods) and `checkpointer.ts` (relocating
alongside this package to `kotoba-lang/checkpointer`, which depends on this
package the same way it did locally). `etzhayyim-sdk`'s own `src/ipfs.ts`
becomes a thin re-export shim (`export * from "@etzhayyim/ipfs/ipfs"`) so
`index.ts`'s existing relative import and the public `@etzhayyim/sdk/ipfs`
subpath keep resolving unchanged.

The initial relocation was a **physical move only** (TypeScript unchanged,
no dedicated tests existed for this module in `etzhayyim-sdk` to bring
along). A CLJC port has since landed (see below) and is now the canonical
Clojure/babashka/JVM/CLJS implementation going forward.

**`dist/` is committed** (see `kotoba-lang/pqh`'s README for the rationale
— git-dependency consumers in `allow-scripts`-gated environments never run
the `prepare` build step).

## Clojure/CLJC port

`src/kotoba/lang/ipfs.cljc` is a 1:1 CLJC port of the three functions above
(`pin-blob`/`fetch-blob`/`node-info`), following the `kotoba.lang.*`
namespace convention established by `kotoba-lang/crypto`:

- **JVM/babashka** branch (`#?(:clj ...)`): synchronous, returns plain
  values (not futures/promises). Uses `babashka.http-client` for the HTTP
  calls (including Kubo's multipart `/api/v0/add` upload) and `cheshire`
  for JSON — the HTTP/JSON stack this ecosystem's other `.cljc` repos
  already use.
- **CLJS** branch (`#?(:cljs ...)`): mirrors the original TS 1:1 —
  browser-native `fetch`/`Blob`/`FormData`/`JSON.parse`, no third-party
  deps. Every function returns a `js/Promise`, matching the original TS
  functions all being `async`.

Tests: `test/kotoba/lang/ipfs_test.clj` (plain `.clj`, JVM-only — the test
infra needs `com.sun.net.httpserver.HttpServer`, part of the JDK, which
isn't on babashka's restricted class whitelist) spins up a real,
dependency-free mock Kubo server and exercises `pin-blob`/`fetch-blob`/
`node-info` against it end-to-end. Run with `clojure -M:test`.

The TypeScript implementation (`src/ipfs.ts` + committed `dist/`) is kept
as-is: it remains the npm-consumable artifact that `etzhayyim-sdk`'s
re-export shim and `kotoba-lang/checkpointer` depend on as a Node/TS git
dependency, and this repo has no reason to force those consumers onto a
JVM/CLJS runtime. New Clojure/babashka/CLJS consumers should use the CLJC
namespace; existing Node/TS consumers are unaffected.

## Development

TypeScript:

```bash
npm install
npm run build
```

Clojure/CLJC:

```bash
clj-kondo --lint src test
clojure -M:test
```

## License

Apache 2.0 + Charter Compliance Rider v3.6 (`/CHARTER-RIDER.md`).
