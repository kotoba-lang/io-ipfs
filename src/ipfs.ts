/**
 * @etzhayyim/sdk/ipfs — IPFS pin/fetch helpers.
 *
 * v0.1.0: real Kubo HTTP API integration. Default targets a local Kubo
 * daemon (the etzhayyim fleet's simeon node at port 5001 / 8080).
 *
 * Per ADR-2605171800 Stage 4.
 */

export interface IpfsConfig {
  /** Kubo HTTP API base URL, e.g. http://simeonnomac-mini.local:5001 */
  apiUrl: string;
  /** Gateway base URL for blob fetch. Default same host port 8080. */
  gatewayUrl?: string;
}

export interface PinResult {
  cid: string;
  size: number;
}

/**
 * Pin a blob (or arbitrary bytes) to Kubo.
 * Returns the resulting CID.
 */
export async function pinBlob(
  apiUrl: string,
  blob: Blob | Uint8Array | string
): Promise<PinResult> {
  let body: Blob;
  if (typeof blob === "string") {
    body = new Blob([blob], { type: "application/octet-stream" });
  } else if (blob instanceof Uint8Array) {
    // Cast: TS 5.7+ tightened Uint8Array → BlobPart typing
    // (ArrayBufferLike vs ArrayBuffer); at runtime any Uint8Array is
    // a valid BlobPart.
    body = new Blob([blob as BlobPart], { type: "application/octet-stream" });
  } else {
    body = blob;
  }

  const form = new FormData();
  form.append("file", body);

  const res = await fetch(
    `${apiUrl.replace(/\/+$/, "")}/api/v0/add?pin=true&cid-version=1`,
    { method: "POST", body: form }
  );
  if (!res.ok) {
    throw new Error(
      `[etzhayyim-sdk/ipfs] pin failed: ${res.status} ${await res.text()}`
    );
  }
  const text = await res.text();
  // Kubo returns NDJSON; one line per file. Last line is the root.
  const lines = text.trim().split("\n").filter(Boolean);
  const last = JSON.parse(lines[lines.length - 1]);
  return {
    cid: last.Hash as string,
    size: Number(last.Size),
  };
}

/**
 * Fetch a blob from Kubo gateway.
 */
export async function fetchBlob(
  gatewayUrl: string,
  cid: string
): Promise<Blob> {
  const res = await fetch(
    `${gatewayUrl.replace(/\/+$/, "")}/ipfs/${encodeURIComponent(cid)}`
  );
  if (!res.ok) {
    throw new Error(
      `[etzhayyim-sdk/ipfs] fetch failed: ${res.status} ${await res.text()}`
    );
  }
  return await res.blob();
}

/**
 * Check the connected Kubo node is reachable.
 * Returns the node's peer ID + version.
 */
export async function nodeInfo(
  apiUrl: string
): Promise<{ id: string; version: string }> {
  const v = await fetch(`${apiUrl.replace(/\/+$/, "")}/api/v0/version`, {
    method: "POST",
  });
  if (!v.ok) {
    throw new Error(`[etzhayyim-sdk/ipfs] node unreachable: ${v.status}`);
  }
  const versionData = await v.json();

  const idRes = await fetch(`${apiUrl.replace(/\/+$/, "")}/api/v0/id`, {
    method: "POST",
  });
  const idData = await idRes.json();
  return { id: idData.ID as string, version: versionData.Version as string };
}
