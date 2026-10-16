# COLONIAL & AE FLEET — Round 12 Pre-Review Synthesis

Date: **19 AUG 2026**  
Job: `2cb2734e-a39e-479e-9ca1-90109ceeb6ec`  
Providers requested: `qwen`, `gemini`, `chatgpt-free`

All three runs reached API status COMPLETED. Qwen took about 12 minutes. ChatGPT Free first entered `WAITING_MANUAL`; the documented continue endpoint completed the run. The complete raw ZIP is `round-12-pre-review-raw.zip`.

ChatGPT Free is **CONTAMINATED / EXCLUDED FROM CONSENSUS**. Its stored response discusses a stale Round-10 upstream-web-check, repeatedly says Round 10 must precede Routing Controller/HUB, and does not answer the Round 12 questions. No replacement opinion is fabricated.

| Topic | Codex pre-review | Qwen | Gemini | ChatGPT Free | Repository evidence | Decision |
|---|---|---|---|---|---|---|
| Persistent HUB policy | Reject | Reject; C backed only by A exact edges | Reject; C compiles to A | Contaminated | Q3 edges already persist all reconstruction facts | No policy/second graph |
| Exact edge authority | `QuantumTopologyRegistry` only | Same | Same | Excluded | Registry + reconciler already arbitrary-E | Exact edges remain sole truth |
| Anchor persistence | Explicit request, no separate authority | Request only; optional non-authoritative receipt | Infer from exact star | Excluded | Edge set is sufficient for reconstruction | No authoritative anchor field; UI may derive only when unambiguous |
| Automatic anchor replacement | Never | Never | Never | Excluded | Reconciler attempts only persisted pairs | Out of scope/failover |
| Authoring permission | BUILD/Q4 | BUILD/Q4; Router initiator at most | BUILD; dedicated authoring workflow | Excluded | Round 11 removed QUANTUM_ROUTE-only create | Server-side BUILD is mandatory; Router remains VIEW/repair |
| Production selection | Explicit non-authoritative selector needed | Prefer transient Q4 draft/one-time ticket; warns per-card sequential writes are not batch atomic | Recommends anchor interaction annotating member cards | Excluded | Current card has no lane/anchor and current fresh activation is PAIR-only | Do not use implicit order. A narrow Q4 selector is required; every committed mutation must be atomic in its declared unit |
| SECURED/LEGACY | SECURED only | SECURED only | SECURED only | Excluded | Router binding is UNBOUND on LEGACY | HUB SECURED-only in Round 12 |
| Mk II/HUB coupling | Orthogonal | Orthogonal | Orthogonal | Excluded | CapacityTier lives on nodes, topology in registry | No topology tier enum |
| Mixed tiers | Allow; native minimum | Allow if native proof | Allow | Excluded | Both endpoint nodes are on the real path | Mk I↔Mk II <=32; Mk II↔Mk II <=64, test required |
| Edge capacity object | None | None; blocker if native pathing cannot express it | None | Excluded | `GridConnection` is not a capacity authority; node bottlenecks drive pathing | Reuse native nodes/pathing only |
| Failure semantics | Edge-local | Edge-local | Edge-local | Excluded | materializer/reconciler iterate exact edges independently | One bad member cannot delete healthy arms |
| Complexity | O(N), N-1 | O(N), N-1 | O(N), N-1 | Excluded | Single edge-list pass | 100 endpoints → 99 edges proof |
| Future failover | Separate policy round | Separate | Separate | Excluded | No policy layer exists | Not Round 12 |

## Consensus

- Proceed only with HUB as an explicit authoring template that leaves exact Q3 star edges and no persistent HUB object.
- The anchor is explicit user intent; unload/destruction never selects a substitute.
- HUB authoring is SECURED and BUILD/Q4 gated. QUANTUM_ROUTE remains existing-intent repair, not creation.
- Capacity and topology remain independent. Native AE2 nodes/pathing must enforce the minimum; no edge pool or parallel calculator.
- Reconciliation/status are O(V+E), runtime-derived and edge-local.

## Disagreements and blind spots

- Qwen rejects card hints as a sufficient batch because sequential activations can leave a partial star. Gemini recommends anchor-annotated cards but does not resolve that batch-atomicity objection. Repository evidence supports Qwen's objection if the advertised operation is “create this complete N-member HUB.”
- A safe alternative is to make each explicit “join this member to this anchor” request its own fully validated atomic operation. Earlier successful joins are then completed user mutations, not partial residue of a later failed join. This is a different Option-D authoring semantic and must be named explicitly if chosen; it cannot masquerade as an all-at-once batch.
- Providers cannot verify actual BE registration, node bottlenecks, persistence, security guards, or tests.

## Architecture gate

**PROCEED WITH CONDITIONS.** The backend authority model passes:

1. exact Q3 edges are the sole persistent topology truth;
2. no persistent HUB policy/mode/member registry;
3. explicit anchor selection, never load/UUID/BlockPos/registration order;
4. server-side BUILD/Q4 authoring and SECURED profile; Controller stays VIEW/repair;
5. an advertised multi-member batch must be validate-all + atomic/rollback; alternatively, an explicit member-join operation may commit exactly one member/edge atomically and report that narrower semantic;
6. no QNB, parallel grid/pathing, automatic failover or O(N²);
7. real PathingService must prove Mk II 64 and mixed-tier 32 bottleneck before PASS.

The next implementation decision is the smallest production selector that can satisfy condition 3 without making a card authoritative. If no narrow flow exists, Round 12 becomes BLOCKED before HUB production edits.
