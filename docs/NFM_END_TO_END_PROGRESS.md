# NFM End-to-End Progress

Master progress ledger. Statuses: PENDING · RUNNING · COMPLETED · PARTIAL · BLOCKED · FAILED · NOT_APPLICABLE.
Evidence-based only; no results are claimed without data.

| PHASE | STATUS | RESULT / BLOCKER | NEXT ACTION |
|---|---|---|---|
| 0 Read rules + dependency map | COMPLETED | docs + code inspected | — |
| 1 Execution safety (no OpenCode-owned backend) | COMPLETED | backend assumed external; health `UP` | — |
| 2 Master job control | PARTIAL | per-source async job + checkpoint/status exist (`EventCollectionJobService`); no single "master all-phase" controller | optional |
| 3 Global timeout policy | COMPLETED | `app.research.events` bounds; standard HTTP timeouts; bounded API polling | — |
| 4 Checkpoint/resume | COMPLETED | `research_event_collection_checkpoint`; failed batch not marked completed | — |
| 5 BLS completion | BLOCKED | parser fixed + checkpoint reset to 2025-12; **needs backend restart** to run | restart backend → `POST /events/collect` |
| 6 BEA PCE | BLOCKED (INELIGIBLE) | BEA historical schedule = date-only (no exact time) | provide exact-time source/file |
| 7 BEA GDP | BLOCKED (INELIGIBLE) | same | same |
| 8 Census Retail Sales | BLOCKED (INELIGIBLE) | date-only | same |
| 9 DOL/ETA Jobless Claims | BLOCKED (INELIGIBLE) | date-only | same |
| 10 FOMC / Fed statements | IMPLEMENTED, NOT EXECUTED | adapter built; EXACT time read from statement page; **needs restart** | restart → collect |
| 11 Central banks (ECB/BoE/BoJ/BoC/RBA/SNB) | NOT_IMPLEMENTED | press RSS current-only; per-bank historical parsing not built | build adapters |
| 12 Crypto structural events | NOT_IMPLEMENTED | curated records required | curate with provenance |
| 13 Regulatory events | NOT_IMPLEMENTED | curated records required | curate with provenance |
| 14 Security events | NOT_IMPLEMENTED | curated official incident reports required | curate with provenance |
| 15 Source failure isolation | COMPLETED | each adapter independent; statuses recorded | — |
| 16 Event normalization | COMPLETED | `NormalizedEvent` → EVENT JSONL → `NewsEvent` | — |
| 17 Event data quality | PARTIAL | 113/113 EXACT; 0 dup; report produced | — |
| 18 Event coverage gate | PARTIAL | `EVENT_DATA_PARTIAL` (macro-only, effectively 1 active source) | broaden sources |
| 19 Decision to backtest | BLOCKED | insufficient category coverage; **no scoped run executed** (restart also pending) | after coverage/restart |
| 20 Baseline config freeze | COMPLETED | `NFM_FUTURES_V1` defaults recorded | — |
| 21 Baseline backtest | BLOCKED | no event coverage + restart pending | after gate |
| 22 Category analysis | BLOCKED | depends on 21 | — |
| 23 Regime analysis | BLOCKED | depends on 21 | — |
| 24 Cost robustness | BLOCKED | depends on 21 | — |
| 25 Walk-forward | BLOCKED | depends on 21 | — |
| 26 Out-of-sample | BLOCKED | depends on 21 | — |
| 27 Sensitivity | BLOCKED | depends on 21 | — |
| 28 Failure analysis | BLOCKED | depends on 21 | — |
| 29 Paper-trading readiness (controls) | PARTIAL | controls verified in code; no observations | — |
| 30 Paper trading (30-day) | NOT_APPLICABLE | gated on validation | — |
| 31 Readiness gates | COMPLETED | see final report | — |
| 32 Testing | COMPLETED | 471 PASS / 0 FAIL / 0 ERR / 2 SKIPPED | — |
| 33 Reproducibility | PARTIAL | dataset + config versions tracked; backtest not run | — |
| 34 Documentation | COMPLETED | this + quality + final reports | — |
| 35 Master progress report | COMPLETED | this file | — |
| 36–39 Hang/job/parallelism/DB safety | COMPLETED | bounded job, single-flight worker, checkpointed | — |
| 40–43 Git / no false completion / blocked-source policy | COMPLETED | honest statuses; no fabrication | — |

## Backend lifecycle
`http://localhost:8080` is externally managed. OpenCode does not start/restart/kill it.
The current running jar predates the BLS parser fix and the Fed adapter, so real
collection is **pending a user restart**.
