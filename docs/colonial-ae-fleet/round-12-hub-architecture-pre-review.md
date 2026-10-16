# COLONIAL & AE FLEET — Round 12 HUB Architecture Pre-Review

Timestamp: **2026-08-19T11:23:00+03:00**  
Author: **Codex own analysis, recorded before reading Round 12 provider answers**

## Repository archaeology

1. A `QuantumLane` has no endpoint-count limit. Its membership is a `LinkedHashSet`, and its exact undirected edges are a list with unordered-pair idempotency.
2. Round 10's PAIR limit lives in `QuantumCredentialService.findOrCreatePairLane`: it selects a lane with fewer than two endpoints.
3. `findOrCreatePairLane` is explicitly PAIR-specific; it is not a Q3 registry invariant.
4. `maybeCompletePair` automatically adds the only edge when the lane reaches exactly two endpoints.
5. Q3 can store three or more endpoints without changing ID, profile, lane, or edge persistence contracts.
6. Exact edges are structurally created by `QuantumTopologyRegistry.addEdge`; production authoring currently occurs only as the BUILD-gated consequence of `activateFreshPhysicalEndpoint`. Package-private Round 11 test helpers are not a production surface.
7. There is no current production BUILD-gated API that atomically authors an explicit N-member HUB.
8. Consequently no current actor can legitimately author N-1 HUB edges. Giving that power to `QUANTUM_ROUTE` would regress Round 11.
9. Exact intent can structurally be removed through `QuantumTopologyRegistry.removeEdge`; no player-facing Controller delete operation exists.
10. `QuantumLane.Edge` is undirected; `sameUnorderedPair` defines identity.
11. Edge insertion order is deterministic for a given authoring call but is not semantic. Endpoint `Set.copyOf` iteration order must not choose an anchor.
12. Persisted edge order is incidental; the set of unordered pairs is the topology fact.
13. A lane can already store a star with N endpoints and N-1 edges.
14. `QuantumLaneReconciler` is already arbitrary-E, single-pass O(E), load-order tolerant and idempotent.
15. Round 11 operational status assumes exactly two endpoints and otherwise returns `UNSUPPORTED_IN_ROUND_11`.
16. PAIR hardcoding exists in fresh provisioning, status DTO/derivation, mutation validation, Router method names/menu text, and Round 10/11 tests.
17. `QuantumRouteMaterializer.materializeLane` is not pair-specific; dematerialization is exact per edge and endpoint invalidation scans only that lane's edges.
18. `QuantumEndpointOnlineTable` is O(1) expected lookup in hash maps and has no topology copy.
19. Mk I sets `DENSE_CAPACITY` and `CapacityTier.STANDARD` on its one managed node.
20. Minimal Mk II is a second block definition accepted by the same BE type; the BE derives `CapacityTier.QUANTUM` from the block variant. No copied BE or tier enum is needed.
21. HUB does not require a special data-plane host block. The explicit anchor is an ordinary endpoint identity.
22. Ordinary endpoint blocks can carry topology semantics separately from capacity tier, but an explicit production authoring selector is still required.
23. An anchor endpoint is not an authority. It is only the endpoint named in each exact star edge; unload/destruction cannot invent replacement edges.
24. No persistent HUB policy is required for Round 12. Exact Q3 edges already contain every fact needed for restart and repair. The UI may call the shape a HUB without persisting that label.

## Ownership table

| Fact | Authority | Persistence |
|---|---|---|
| Profile/lane/endpoint IDs and membership | `QuantumTopologyRegistry` | SavedData |
| Exact star edges | `QuantumTopologyRegistry` | SavedData |
| Credential state/binding | `QuantumCredentialRegistry` via `QuantumCredentialService` | SavedData |
| Permissions/domain | Existing Security registry/service | Existing persistence |
| Loaded endpoint node | `QuantumEndpointOnlineTable` | Runtime only |
| Materialized connection | Existing Grid/GridConnection | Runtime only |
| Per-edge/lane operational status | Derived routing projection | Never authoritative |
| Capacity | Each endpoint node's existing `CapacityTier` | Block variant/runtime node configuration |

## Options

### A — one-shot explicit HUB authoring to exact edges

An actor explicitly names one anchor and members. The server validates the complete request under BUILD/Q4 before mutation, then writes only anchor-member exact edges. Restart reads those edges. Membership changes are new explicit mutations. Anchor loss degrades only its edges and never triggers replacement. Complexity is O(N). This preserves Q3 and is compatible with a future separate failover-policy round.

Risk: the current encoded credential card has no lane/anchor selector, so a production flow must add a narrow, non-authoritative selection mechanism. Activation order, UUID order and BlockPos are forbidden substitutes.

### B — persistent HUB policy plus exact realization

A second record would persist mode, anchor and members, while Q3 edges also persist the realization. Making policy authoritative changes Q3 and requires reconciliation/conflict/migration semantics; making it metadata risks stale duplicated truth. It adds no Round 12 capability that exact edges lack. Restart and membership changes become more complex, and future policy code could silently rewire after anchor loss.

Decision: reject for Round 12 absent new evidence.

### C — HUB as UI/provisioning template

This is the user-facing expression of A: a sanctioned BUILD-gated workflow gathers explicit anchor/member intent and atomically invokes exact-edge authoring. No HUB mode survives after the operation. The Router remains observe/repair only. This offers understandable UX without a second graph.

Risk: a selector carried on an item/card must remain a request hint, never credential/topology authority, and must be revalidated server-side.

### D — implicit first endpoint as anchor

Rejected. Choosing by activation/registration order makes topology depend on an incidental runtime sequence and cannot disambiguate multiple HUBs in one profile.

## Preliminary choice

Choose **A implemented through C**: HUB is an explicit one-shot BUILD-gated provisioning template. The explicit anchor is supplied by user intent; after successful atomic mutation only Q3 membership and N-1 exact edges persist. There is no persistent HUB policy or topology-mode enum. Routing Controller remains VIEW/repair and never receives topology authority from QUANTUM_ROUTE.

The safest production interaction appears to be a narrow Q4-mediated selector: an issued per-endpoint credential card may carry a non-authoritative target lane/anchor hint obtained through an explicit interaction with the intended anchor; activating the new endpoint revalidates profile, lane, anchor, credential, BUILD permission and all intended edges before mutation. This is a candidate, not yet an implementation decision. If repository/API review cannot make that flow small and unambiguous, Round 12 must stop rather than choose an implicit anchor.

## Capacity decision

Capacity and topology stay orthogonal. Mk II uses `CapacityTier.QUANTUM` on the same dense endpoint node. A virtual edge crosses both endpoint nodes, so native pathing should impose `min(endpoint A, endpoint B, upstream, downstream, P2P, controller)`. Mk I↔Mk II therefore caps at 32 and Mk II↔Mk II at 64 without a parallel calculator. This remains subject to real PathingService proof.

## Atomicity

Validate the entire HUB request before any mutation: profile, secured domain, BUILD actor, unique members, explicit anchor included, every endpoint/credential binding, same lane/profile, and planned non-self exact edges. Only then add N-1 edges. Since `addEdge` can still return false, production code must either prove post-validation mutation cannot fail on the single server thread or roll back only edges newly added by this call. An API failure may not leave a partial star.

## Failure semantics

- Anchor unload/destruction: exact intent remains; all anchor edges degrade; members never connect to one another.
- One member unload/revoke/suspend/cross-domain rejection: only its edge becomes unavailable; healthy edges remain.
- Router unload/destruction: no topology or transport effect.
- Restart: no actor/Router; reconstruct only the same persisted edges in arbitrary load order.
- No automatic anchor replacement, temporary edge, failover, MESH, or optimizer.

## Stop conditions

Stop if explicit production authoring requires an implicit anchor, QUANTUM_ROUTE-only creation, duplicated policy graph, re-parenting existing endpoint identity, parallel grid/pathing, QNB edits, O(N²), non-atomic partial intent, Router/actor-dependent restart, or if native pathing cannot enforce symmetric mixed-tier bottlenecks.
