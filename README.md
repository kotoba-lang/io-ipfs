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

This is a **physical move only** (TypeScript unchanged) — no dedicated
tests existed for this module in `etzhayyim-sdk` to bring along, and a CLJC
port is deferred to a later, separate task.

**`dist/` is committed** (see `kotoba-lang/pqh`'s README for the rationale
— git-dependency consumers in `allow-scripts`-gated environments never run
the `prepare` build step).

## Development

```bash
npm install
npm run build
```

## License

Apache 2.0 + Charter Compliance Rider v3.6 (`/CHARTER-RIDER.md`).
