# AI-assisted unit-test generation: with vs. without execution feedback

Runnable pipeline (**Java 17+ port, no third-party dependencies**) for the paper (Cond-A one-shot vs. Cond-B bounded compile/run feedback).
**This repository contains no results.** `data/results.csv` is header-only; running the steps
below on your own machine creates the real file. Never edit that file by hand.

## What is here
| Path | Purpose |
|---|---|
| `candidates/exp/*.java` | 24 small authored candidate methods (4 per stratum), each with a Javadoc contract. Smoke-tested against their own examples. |
| `src/main/java/pipeline/SelectMethods.java` | SHA-256 hash-order selection, 3 per stratum -> `data/methods.csv` (18 methods). Also writes 2 held-out pilot methods. |
| `prompts/` | Frozen initial + repair prompt (Listing 1). Hashed into `prompt_version`. |
| `template/pom.xml` | The single Maven build for both conditions (JUnit 5, JaCoCo, PIT; versions pinned). |
| `docker/` | Sandbox image (network-less at run time) and an audit probe. |
| `RunExperiment.java` | Controller: generate, Algorithm 1 repair loop, JaCoCo + PIT, writes `results.csv`, archives everything. |
| `MakeTables.java` + `Stats.java` | Regenerates every result table: exact tie-aware Wilcoxon, bootstrap CI, A12, Holm, Eq. 4/5. |
| `ListSurvivors.java` | Builds the blind equivalent-mutant review sheet (Cohen's kappa is computed from it). |
| `SelfTest.java`, `src/test/java/` | Unit tests + synthetic end-to-end check of the aggregation (clearly labeled synthetic). |

## Run order
```bash
./build.sh                                        # javac only: JDK 17+ on the host, no Maven needed here
./run.sh test                                     # 15 unit tests, must pass
./run.sh selftest                                 # synthetic data; checks the analysis pipeline only

docker build -f docker/Dockerfile -t exp-sandbox:1 .      # needs network once (warms Maven cache)
bash docker/sandbox_probe.sh                              # audit: no secrets, no network, writable paths

export ANTHROPIC_API_KEY=...                              # never commit it
export MODEL_ID=<exact model id you will cite>            # recorded in every row
./run.sh select                                           # then FREEZE data/methods.csv, prompts/, template/
./run.sh run --pilot                                      # 2 held-out methods, 1 repeat -> data/results_pilot.csv
./run.sh run                                              # full run: 18 x 3 repeats, resumable
./run.sh survivors                                        # two reviewers fill data/equivalence_review.csv
./run.sh tables                                           # -> tables/tables.md, table_vii.csv, per_method.csv
```
Options mirror the Python version: `--repeats`, `--model`, `--max-tokens`, `--temperature`, `--only A,B`, `--no-docker`, `--pilot`.
Temperature defaults to 0.0; if your model rejects it use `--temperature none` and say so in the paper.
Use `--no-docker` only for debugging: it runs model-written code on your machine without isolation.

## Pilot acceptance (Sec. V-G) -- do this before the freeze
1. Both conditions leave artifacts per method (`artifacts_pilot/<method>/r1/...`).
2. `sandbox_probe.sh` shows no keys in env and "blocked (good)"; JaCoCo and PIT reports appear offline.
3. Break a test on purpose (edit `artifacts_pilot/.../Test.java`, rerun the diagnostic function from `ExpCore.java`): a non-empty excerpt is returned.
4. Check a trial with `repairs == 2` stops at round 2.
5. Hand-compute one pilot row (coverage from `target/site/jacoco/jacoco.xml`, mutants from `pit.log`) and compare with `results_pilot.csv`.

## Design decisions you must reflect in the paper
* **Shared first output.** One first request per (method, repeat); Cond-A's final suite *is* that output and Cond-B starts from it (matches the abstract: "the same first output"). Consequences: B can only improve compile/executable over A, and at most 2 repair requests per initially non-executable suite, so mean extra requests <= 2 x (share of initially non-executable suites).
* **Repeats.** Per-method values are fractions of the 3 repeats (e.g. 2/3 compiled). Eq. (1) and the "binary" wording should say so; with repeats the paired differences are not limited to {-1,0,+1}.
* **Prompt.** The initial prompt adds "class named `<Class>Test` in package `exp`" (needed to build mechanically); update Listing 1.
* **Classification.** `TestCodeError` = missing/duplicate symbols at run time, instantiation/parameter-resolution errors, no tests found. Any failing test whose cause is an unexpected exception or a failed assertion counts as an assertion failure (not repaired, not fed back).
* **Quarantine.** Failing tests are disabled with `@Disabled` (parameterized methods are disabled as a whole); if one cannot be located, the whole suite is replaced by an empty placeholder test so the mutant universe stays identical. Coverage is taken from the *unmodified* final suite; mutation score from the *quarantined* suite; the strict variant scores 0 for any suite with a failing test.
* **Invalid-oracle count** includes failures that might be genuine defects; the reference methods were smoke-tested against their contracts, so this is expected to be rare, but the assertion-failure review in Appendix A still applies.
* **Mutation counting.** `mut_killed` = KILLED + TIMED_OUT + MEMORY_ERROR; NON_VIABLE and RUN_ERROR are excluded; SURVIVED and NO_COVERAGE count as surviving. PIT: DEFAULTS group, fixed timeouts.
* **Elapsed time** = first request to end of evaluation. When no repair is needed A and B do identical work, so their times are identical for that trial.
* **Sandbox.** Network-less, non-root, no capabilities, CPU/memory/pid limits, no env secrets. The root filesystem is not read-only (Maven needs a writable cache); the container is discarded after each call.
* Equivalence review adjusts `mut_eligible` at aggregation time, never inside `results.csv`.

## Known limits of this code
Written and unit-tested without Docker, Maven, PIT, or API access, so those integration paths have **not been run**. The parsers are tested on hand-written fixtures of the Maven/JaCoCo/PIT/Surefire formats; the pilot is how you confirm they match real output. Expect to fix small things on the first pilot run.

## Notes on the Java port
* **Verified against the Python original:** `./run.sh select` reproduces `data/candidates.csv`, `methods.csv`, `pilot_methods.csv` and `selection_log.txt` byte-for-byte (apart from line endings). On the same synthetic `results.csv`, every statistic in `table_vii.csv` (means, deltas, exact Wilcoxon p, Holm, A12, counts, SD, median, IQR) and `per_method.csv` matches the Python output exactly.
* **Bootstrap CIs are not bit-identical to the Python version.** Java uses `java.util.Random(20260101)`, Python used NumPy's PCG64; the resamples differ, so CI endpoints differ by Monte-Carlo noise (typically < 0.3 pp at 10,000 resamples). They are still reproducible from the fixed seed. Say which implementation produced the paper's CIs.
* **Statistics need no libraries:** the exact tie-aware Wilcoxon uses `BigInteger` subset-sum counting; unit tests check it against brute-force sign enumeration.
* **XML parsing never fetches DTDs.** JaCoCo reports carry a `DOCTYPE` pointing at `report.dtd`; the parser is configured to ignore it (offline sandbox, no XXE).
* **The Anthropic call is plain HTTPS** (`java.net.http`, `anthropic-version: 2023-06-01`, 4 attempts with back-off), so no SDK is needed. The JSON reader/writer is a small built-in class.
* **Not run here:** Docker, real Maven/JaCoCo/PIT and the live API. The A/B orchestration (shared first request, repair loop, coverage/PIT parsing, archiving) was exercised with a scripted LLM and a fake `mvn` (`src/test/java/pipeline/SmokeTest.java`); the real-tool paths are confirmed by your pilot, as before.
