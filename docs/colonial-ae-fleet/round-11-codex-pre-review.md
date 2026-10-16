# COLONIAL & AE FLEET — Round 11 Codex Pre-Review

Timestamp: 2026-08-19T09:52:00+03:00

Scope: independent architecture review recorded before reading any Qwen, Gemini, or ChatGPT Free response. The working tree already contains a Round 11 implementation; this review treats it as pre-existing and does not assume its report claims are correct.

## Ownership decision

| State | Authority | Persistence |
|---|---|---|
| NetworkProfileId, QuantumLaneId, QuantumEndpointId | QuantumTopologyRegistry | SavedData |
| CredentialId, revision, lifecycle, endpoint binding | QuantumCredentialRegistry via QuantumCredentialService | SavedData |
| Exact sparse route edges (intent) | QuantumTopologyRegistry | SavedData |
| Loaded endpoint presence and current node/location | A server-scoped runtime index/projection | Runtime only |
| Materialized GridConnection | QuantumLaneReconciler/GridHelper and the live grid | Runtime only |
| Route operational status | Derived projection of intent, presence, credential state, and actual connection | Runtime only |
| Permissions/domains | SecurityDomainRegistry/SecurityService | Existing security persistence |

## Decisions

1. A new persistent RouteRegistry is neither needed nor safe if it repeats exact edges. QuantumTopologyRegistry is sufficient authority for identity and intent. A separate service is acceptable only as a stateless authorization/projection/materialization facade.
2. Operational status must not be persisted. It should be recomputed from authoritative registries and live endpoint/GridConnection facts. UI snapshots may be ephemeral and invalidated on each request; no tick-driven global scan.
3. A persistent presence registry is unnecessary. Registered identity already persists; presence means that the physical endpoint node is loaded now. A server-scoped O(1) endpoint-to-live-node table is appropriate if conflict-safe and cleaned on unload.
4. QuantumEndpointLocation may persist as last registered/known physical metadata only if the existing Q3 contract already does so. Exact current presence/location must come from the live table. Unload is not movement or deletion.
5. Router unload and destruction remove only access to its control UI. They must not remove profile/lane/endpoint identity, credential binding, edge intent, or a healthy materialized pair.
6. QUANTUM_VIEW should expose profile-local IDs, lane/edge intent, and coarse status, but not exact BlockPos. Exact coordinate disclosure requires QUANTUM_ROUTE (SECURITY inherits it). This minimizes cross-site information leakage while preserving operator diagnostics.
7. Advanced routing should require an unambiguous SECURED profile binding. A LEGACY router should remain UNBOUND rather than become a global/open topology browser. This is a deliberate narrow privacy rule for remote topology, not a rewrite of general LEGACY AE2 openness.
8. Manual reconcile requires QUANTUM_ROUTE and may only attempt to materialize existing persisted intent. It must not create/delete edges, rebind credentials, select alternate paths, or implement failover.
9. Intent existence, live materialization, and operational status must be represented by different types/fields and proven independently. No persisted `materialized=true` flag is acceptable.
10. Credential suspension/revocation must prevent new materialization. If an already materialized connection remains live after the state transition, that is a security blocker and requires narrow teardown hardening independent of router presence.
11. Round 11 scope is PAIR-only Routing Controller block/BE/menu/screen, profile-local status projection, authorized existing-intent repair, lifecycle proof, and bounded regressions. HUB, MESH, failover, Mk II-IV, relocation, global browsing, and final power balance remain out of scope.

## Architecture gate

The design is viable if the repository confirms one authoritative edge store, runtime-only presence/status, actor-independent restart reconciliation, router-independent data plane, existing SecurityService authorization, and no QNB/pathing changes. Multiple routers are safe only if they are stateless clients of the same service/registries; a targeted idempotency test should prove two controllers cannot create duplicate connections or divergent state.

## Stop conditions

Stop if implementation needs a duplicate RouteRegistry, parallel IGrid/PathingService/security/credential authority, persistent runtime status, online router/player for restart, QNB modification, O(N²) topology, coordinate disclosure without a defensible permission rule, LEGACY global enumeration, router-owned teardown, or if repeated reconciliation duplicates GridConnections.

