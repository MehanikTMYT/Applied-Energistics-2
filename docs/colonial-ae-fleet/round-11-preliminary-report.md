# COLONIAL & AE FLEET — Round 11 Preliminary Report

Date: 19 AUG 2026

## A. Baseline

- Repo: `/root/home/forks/ae-stack/core/Applied-Energistics-2`
- Branch/HEAD: `main` / `45f315517ea346efc0babd02c85c6b9d32dc8acf`
- Minecraft / NeoForge: `26.1.2` / `26.1.2.21-beta`
- Initial status: 640 entries (48 modified, 592 untracked), staged empty
- Forced baseline: 837/837 JUnit, 79/79 GameTests, build PASS, diff-check clean
- This proves the tree already contained a pre-existing Round 11 implementation; Codex does not claim authorship of it.

## B. Aggregator integration

- Local REST base: `http://127.0.0.1:3100/api`; production OpenAPI server: `https://aggregate.mehhost.ru/api`.
- Health: `GET /health`; providers: `GET /providers`; create: `POST /jobs` with `{title,task,providerIds,models?}`.
- Job polling: `GET /jobs/{id}`. Per-run terminal states include COMPLETED/FAILED/WAITING_MANUAL; job may be COMPLETED/PARTIAL_SUCCESS/FAILED/WAITING_MANUAL.
- Retry/continue: `POST /jobs/{id}/runs/{provider}/retry|continue` (blocking).
- Results: `GET /jobs/{id}/artifacts`, then `GET /artifacts/{id}`; ZIP at `/jobs/{id}/download`. Task and response Markdown are persisted in MySQL metadata + MinIO object storage under dated job keys. Raw provider snapshots are supported internally but normal job runner currently stores response Markdown only.
- No standalone CLI exists; curl/OpenAPI is the scripting interface. Web frontend calls the same REST API.
- Pre-review job: `76871e7d-3a25-4814-ad18-fa3180d67559`; supplemental ChatGPT artifact retry: `0ddab3db-d555-43f8-ac71-f69d0c3950d6`.

## C–D. Architecture and external gate

Codex and reviewers agree: QuantumTopologyRegistry alone owns typed identity and exact sparse intent; QuantumCredentialRegistry/Service owns credentials; SecurityDomainRegistry/Service owns authorization; QuantumEndpointOnlineTable is runtime-only presence; GridConnection is runtime materialization; QuantumRoutingService derives status. Router is stateless control plane and is not required for pair liveness/restart.

Decision: no exact BlockPos is transmitted. LEGACY Controller is UNBOUND. QUANTUM_VIEW reads coarse profile-local state; QUANTUM_ROUTE may only request repair of existing intent. Qwen/Gemini concurred. ChatGPT argued limited legacy diagnostics could be consistent, but privacy-minimal UNBOUND was selected. The ChatGPT GUI contained a relevant response; its artifact extractor repeatedly returned stale Round-10 text, recorded as an aggregator defect.

## E. Archaeology

- Existing Round 11 already supplied Router block/BE/menu/screen/assets, QuantumRoutingService, QuantumRouteMaterializer, runtime projection/status types, 39 JUnit methods and five GameTests.
- No RouteRegistry, parallel IGrid, PathingService, security authority, credential authority, or QNB dependency exists.
- Runtime suspend/revoke dematerialization is independent of Router and preserves identity/intent.
- Endpoint location is persisted identity metadata; loaded node presence is runtime-only.
- Controller binding resolves one SECURED domain/profile; LEGACY/disconnected/ambiguous states are UNBOUND.
- Materializer matches the two exact endpoint nodes plus virtual-connection marker; it does not scan grids. `!isInWorld()` is not globally unique (P2P/QNB/toggle bus also use virtual connections), but no current mechanism forms those connections between these endpoint node instances.
- Found scope defect: Router exposed CREATE/DELETE and changed exact intent using QUANTUM_ROUTE, contrary to the required existing-intent-only Round 11 contract and existing BUILD/Q4 creation boundary.

## F–K. Production architecture and narrow fix

The existing PAIR-only controller remains a status/repair control plane. Codex narrowed it by:

- removing Controller BE create/delete methods;
- removing create/delete menu action keys/handlers/client entry points;
- removing create/delete screen buttons/layout entries;
- reducing service create/remove helpers from public to package-private (retained only for same-package deterministic tests; no production caller remains);
- replacing the route-delete/recreate GameTest with a production-path manual reconcile test: persisted intent survives forced runtime dematerialization; QUANTUM_ROUTE repair restores exactly that edge and storage visibility; repeats do not duplicate connections;
- narrowing unauthorized GameTest assertions to the remaining view/repair surface.

Security: all reads/mutations are server checked; packet carries dimensions but no BlockPos. Repair cannot create intent. Live revoke/suspend teardown remains router-independent. Channel semantics are unchanged: Mk I ceiling remains 32 and all full-path/P2P regressions remain in the 837-test suite. Complexity is O(V+E) snapshot, O(E lane) reconcile, O(1) endpoint lookup; no tick/world scan.

## L–Q. Proof and audit

- Targeted `QuantumRoutingControllerTest`: PASS (39 tests).
- Compile + Spotless Java/JSON: PASS. One line-wrap violation was fixed manually in the changed GameTest; no broad formatter apply was run.
- Forced `cleanTest test`: 837/837 PASS.
- `runGametest`: 79/79 PASS. No flaky test failed. Jade logged its known startup loot-table registry warning without affecting tests.
- `build`: PASS.
- QNB source paths have no diff.
- STOP audit: no duplicate authority, no parallel pathing/grid, no router/player restart dependency, no QNB change, no coordinate leak, no O(N²), no channel generation. HUB/MESH/failover/Mk II-IV remain absent.

Preliminary verdict: **ROUND 11 ROUTING CONTROLLER FOUNDATION — PROVEN WITH LIMITATIONS: PAIR-only status and existing-intent repair; no Controller route creation/deletion, no exact coordinate view, SECURED binding only.**

