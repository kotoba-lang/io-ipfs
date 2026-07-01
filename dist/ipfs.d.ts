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
export declare function pinBlob(apiUrl: string, blob: Blob | Uint8Array | string): Promise<PinResult>;
/**
 * Fetch a blob from Kubo gateway.
 */
export declare function fetchBlob(gatewayUrl: string, cid: string): Promise<Blob>;
/**
 * Check the connected Kubo node is reachable.
 * Returns the node's peer ID + version.
 */
export declare function nodeInfo(apiUrl: string): Promise<{
    id: string;
    version: string;
}>;
