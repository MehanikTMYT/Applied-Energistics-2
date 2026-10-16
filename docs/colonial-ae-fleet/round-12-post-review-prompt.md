# Round 12 post-implementation adversarial review

You are one of three independent reviewers receiving this identical prompt. You have no repository access. Mark claims as PROVIDED FACT, INFERENCE, DESIGN RECOMMENDATION or NEEDS REPO VERIFICATION. Do not claim code inspection.

Existing invariants: exact profile/lane/endpoint/undirected-edge intent persists only in QuantumTopologyRegistry; credentials and binding only in QuantumCredentialRegistry/Service; security only in the existing Security registry/service; presence and operational status are runtime projections; live GridConnection is not identity. Quantum and recursive ME P2P never generate channels; effective capacity is the minimum real path element. Vanilla QNB is separate and untouched. Round 11 Router is secured-profile-local VIEW plus QUANTUM_ROUTE repair of existing intent, not topology authoring or a data-plane hop.

Round 12 architecture: HUB is not a persisted mode/policy. It is an explicit incremental operation: on a fresh endpoint, the actor supplies a fresh member credential plus an already-bound explicit anchor credential. Server-side Q4 service validates both credentials/revisions/states, same secured Profile, anchor endpoint/lane and BUILD permission before mutation. It registers one member in the anchor lane, adds exactly one exact anchor-member edge, then activates the member; delegated failure removes only that new edge and discards the fresh unactivated endpoint. Each successful join is one committed user mutation. Existing joins are not rolled back if a later separate join request fails. Cards request IDs/revisions but registries remain authoritative. Router gains no CREATE/DELETE action.

No authoritative anchor field, HUB mode or member graph is persisted. The exact star edges alone reconstruct. Anchor unload/destruction leaves intent degraded and never rewires. Reconciler/materializer iterate arbitrary exact edges independently and idempotently. Runtime lane status derives endpoint count, intended/materialized edges and edge-local reasons, without BlockPos. Suspend/revoke one member tears down only incident live arms; other arms remain. Resume retries only the same exact intent. Cross-domain bad arms are rejected by existing pre-merge security while valid arms remain.

Mk II is the existing endpoint class parameterized with existing CapacityTier.QUANTUM=64; Mk I remains STANDARD=32. No topology tier enum or edge calculator. Both endpoint nodes lie on the native path. Real PathingService tests prove MkII/MkII 64, MkI/MkII 32, upstream standard/P2P-like 32, downstream dense 32, and 47 already used of 64 leaves 17. A 100-endpoint structural test yields 99 edges.

Production scope/files: QuantumCredentialService; QuantumEndpointBlock/BE; AEBlockIds/AEBlocks/AEBlockEntities; QuantumRoutingService; Routing Controller BE/menu/status packet/screen; Mk II model/recipe/lang/generated resources. Tests add QuantumHubRound12Test, QuantumHubChannelCapacityTest, a real SavedData star round-trip, and six production GameTests including multiple same-profile Routers, 3-endpoint HUB, member/anchor absence, credential revoke, and partial cross-domain rejection.

Validation: branch main at 45f315517ea346efc0babd02c85c6b9d32dc8acf; MC 26.1.2 / NeoForge 26.1.2.21-beta. Forced JUnit 851 cases: 850 passed, one existing skipped, zero failures/errors. Final GameTest retry 85/85; earlier run had one unrelated fe_charger timeout (84/85), while every Round 12 test passed in both. Build/Spotless/resource validation PASS; diff-check clean; QNB untouched. Server reached Done; client loaded and completed F3+T reload. Interactive world/human visual validation is not claimed; Mk II reuses placeholder quantum-ring art.

Adversarially assess:

1. split-brain between HUB concept and Q3 exact edges;
2. sparse/O(N) claim and hidden O(N²);
3. explicit anchor, unload/destruction and accidental failover;
4. whether incremental join semantics honestly satisfy atomicity;
5. Router topology-authoring or BUILD/Q4 bypass;
6. mixed-tier channel generation or asymmetric bottleneck risk;
7. edge-local revoke/suspend and partial cross-domain leakage;
8. restart/load-order nondeterminism and duplicate materialization;
9. multiple Router authority/caches;
10. orphan/leak/unbounded-state risks;
11. missing high-value tests;
12. whether the proposed PROVEN WITH LIMITATIONS scope is too broad.

Return exactly one verdict: ACCEPT, ACCEPT WITH HARDENING, BLOCK NEXT ROUND, or ARCHITECTURE ISSUE. A hardening request must identify a narrow reproducible defect/test; architecture expansion belongs to a later round.
