# COLONIAL & AE FLEET — Round 12 Post-Review Synthesis

Date: **19 AUG 2026**  
Job: `0a4884a8-2d02-46d1-a1e2-ebe54265f6c9`  
Providers requested/completed: `qwen`, `gemini`, `chatgpt-free` / all API COMPLETED

Raw bundle: `round-12-post-review-raw.zip`.

ChatGPT Free is **CONTAMINATED / EXCLUDED FROM CONSENSUS**. Its stored response is stale Round-10 API/pathing advice and never reviews the supplied Round 12 implementation. The documented `/continue` endpoint was used once after WAITING_MANUAL; no replacement answer is fabricated.

| Topic | Codex | Qwen | Gemini | ChatGPT Free | Repository check | Decision |
|---|---|---|---|---|---|---|
| One topology truth | Exact Q3 edges only | Conditionally safe | Safe | Contaminated | No HUB/policy/anchor persistence or Router authority | Accepted |
| Sparse/complexity | O(V+E), 99 edges for 100 endpoints | Wants runtime-cost check | Wants no nested status scan check | Excluded | Status/reconcile single passes; table/map lookup; no tick caller | O(V+E) confirmed |
| Incremental atomicity | One join is one commit | Honest if documented | Valid | Excluded | Validate first; failed delegated activation removes new edge+endpoint | Accepted; not batch transactionality |
| Replay/rollback | Covered | Requests explicit proof | No objection | Excluded | Clone replay forces post-edge activation conflict, leaves one edge/no endpoint residue | Already covered |
| Anchor absence | No rewire | Requests explicit repair proof | Safe | Excluded | Production GameTest removes runtime anchor presence, proves members separate, exact edges unchanged, exact restoration | Already covered |
| Anchor revoke | Incident arms only | Requests proof | Requests proof | Excluded | Newly added JUnit proves both arms down, exact intent/identities and member credentials survive | Narrow hardening applied |
| Router authority | View/repair only | Requests negative checks | Safe | Excluded | No Router create/delete path; existing permission/profile tests and multi-Router GameTest | Accepted |
| Mixed capacity | Native minimum | Requests mixed/oversubscription | Safe | Excluded | Real PathingService: 64 ceiling, mixed 32, upstream 47→17, downstream 32 | Already covered |
| Cross-domain arm | Edge-local fail closed | Requests proof | Safe | Excluded | Production GameTest has valid X-X arm plus rejected X-Y arm and no leakage | Already covered |
| Restart/load order | Exact/idempotent | Requests repeated reload | Safe | Excluded | Disk SavedData star, arbitrary runtime load order, 100 materializations, no alternative/duplicate | Already covered |
| Scope claim | Proven with explicit limits | Accept with hardening | Accept with hardening | Excluded | No world/human visual claim; no failover/MESH/Mk III-IV claim | PROVEN WITH LIMITATIONS |

## Consensus and disagreement

Qwen and Gemini found no architecture issue. Both accepted the one-authority, sparse, explicit-anchor, BUILD/Q4 and native-capacity model. Both asked for anchor credential revocation proof; this was a narrow, reproducible lifecycle test and was added. Qwen listed additional desirable tests, but repository verification showed that replay rollback, anchor absence/no rewire, mixed bottlenecks, cross-domain partial rejection, multi-Router convergence, SavedData restart and 100-cycle idempotence already exist. Its proposed artificial concurrent-thread test is inappropriate because production mutations execute serially on the Minecraft server thread; 100 back-to-back joins cover bounded sequential atomicity.

No post-review finding requires a policy registry, Router authoring, failover, QNB, parallel pathing or other Round 13 work.

## Hardening and revalidation

Added `revokedAnchorTearsDownEveryIncidentArmWithoutDeletingMembersOrIntent`. First execution exposed only an incorrect test expectation (`ACTIVATED` versus the correct healthy `ONLINE` member state); the assertion was corrected. Final forced validation after the test change:

- JUnit: 852 cases, 851 passed, 1 existing skipped, 0 failures/errors.
- GameTests: 85/85 PASS.
- Build/Spotless/resources: PASS.
- `git diff --check`: clean at artifact update time.

Final synthesis verdict: **ACCEPT WITH HARDENING SATISFIED**.
