# Operator quickstart

This repository is the **descriptor and contract** for the airshed actor. It is
not the runtime. What lives here is the actor's identity, the capabilities it
declares, the governance rules it must obey, the AT Protocol lexicons for the
records it writes, and the deployment descriptor that `app-aozora` reads. The
portable data vocabulary is implemented in `kotoba-lang/airshed`.

So an operator's job here is narrow and specific: **keep four file surfaces
saying the same thing, and prove it before shipping.**

```
actor-manifest.jsonld   who this actor is, what it may do, what it may never do
deploy/app-aozora.edn   what the host is told to allow
lex/*.edn               the record shapes it can actually write
README.md               the same promises in prose
```

These drift silently. The manifest can gain a collaboration event kind that no
lexicon can store; the deploy allowlist can carry a capability the manifest
never declared. Nothing at runtime notices — the record just fails to write, or
a permission is handed out that was never declared. The contract gate below
exists to make that drift loud.

## Prerequisites

`nbb` only (walked on v1.5.212). There is no `deps.edn` and no `package.json` in
this repository, so there is nothing to install and no lockfile to resolve — the
gate is pure `.cljc`/`.cljs` over files already committed here.

## 1. Run the gate

From the repository root:

```bash
kbb --backend sci --classpath src:test run_tests.cljs
```

```
Ran 23 tests containing 32 assertions.
0 failures, 0 errors.

airshed actor contract: all green
```

Exit status is `0` on green and `1` on failure, so this is usable directly as a
pre-ship check.

> **Run it from the repository root.** `test/etzhayyim/airshed/repo_test.cljs`
> resolves the descriptor files against `process.cwd()`. Run it from anywhere
> else and it aborts with
> `ENOENT: no such file or directory, open '.../actor-manifest.jsonld'` —
> it does not quietly report a pass on files it never read.

## 2. Read what this actor may and may not do

```bash
kbb --backend sci -e '
(ns q (:require ["node:fs" :as fs] [clojure.string :as str]))
(let [m (js->clj (js/JSON.parse (.readFileSync fs "actor-manifest.jsonld" "utf8")))]
  (println "did      " (get m "@id"))
  (println "runtime  " (get m "runtime") "/" (get-in m ["profile" "agentType"]))
  (println "may do   " (str/join " " (get m "capabilities")))
  (doseq [r (get-in m ["governance" "rules"])]
    (println "rule     " (get r "id"))))'
```

```
did       did:web:airshed.etzhayyim.com
runtime   kotoba-wasm / advisory
may do    airshed.observe airshed.model airshed.propose airshed.audit airshed.collaborate airshed.engage airshed.convene
rule      RULE-AIRSHED-PROVENANCE
rule      RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION
rule      RULE-AIRSHED-MINIMUM-DISCLOSURE
rule      RULE-AIRSHED-VOLUNTARY-PARTICIPATION
rule      RULE-AIRSHED-EVENT-NONBINDING
```

The capability list is the *whole* set of things this actor can call. Every
pipeline step is checked against it, so a step calling anything outside this
list is a violation rather than a new permission.

## 3. Watch the gate refuse

A gate nobody has seen fail is not evidence. Before trusting a green run, make
it go red once. Add a sixth event kind to the manifest and to **nothing else**:

```bash
# in actor-manifest.jsonld, collaboration.eventKinds — append "site-visit"
kbb --backend sci --classpath src:test run_tests.cljs; echo "exit=$?"
```

```
FAIL in (the-committed-descriptor-has-no-violations)
違反 1 件:
  :events/kinds-lexicon-drift — manifest eventKinds ("council-observation" "implementation-workshop" "listening-session" "site-visit" "technical-clinic" "verification-visit") ≠ event kind enum ("council-observation" "implementation-workshop" "listening-session" "technical-clinic" "verification-visit")

FAIL in (event-kinds-match-the-event-lexicon)

Ran 23 tests containing 32 assertions.
2 failures, 0 errors.

airshed actor contract: FAILED
exit=1
```

Two failures, not one: the aggregate rule reports *what* drifted with both sides
printed, and the named test says *which* of the four surfaces to look at. Undo
with `git checkout -- actor-manifest.jsonld`.

## 4. The shape of a correct change

Because the surfaces are checked against each other, most edits here are
two-sided. Adding an event kind is the worked example:

1. `actor-manifest.jsonld` → append to `collaboration.eventKinds`
2. `lex/event.edn` → append the same string to the `:kind` `:enum`
3. re-run the gate → green

Do only step 1 and you get the refusal in §3. Do only step 2 and you get the
same rule from the other direction — the checks are deliberately symmetric, so
"the manifest offers something no record can hold" and "a record shape exists
that the manifest never offers" are both violations.

The same two-sidedness applies to:

| If you change | You must also change |
|---|---|
| `collaboration.engagementLifecycle` | `lex/engagement.edn` `:status` `:enum` |
| `collaboration.eventKinds` | `lex/event.edn` `:kind` `:enum` |
| manifest `capabilities` | `deploy/…` `:deployment/capability-allowlist` |
| manifest `@id` | `deploy/…` `:deployment/actor-did` |
| adding a `lex/*.edn` | `deploy/…` `:deployment/required-contracts` |

## 5. What each violation means

All 13 rules live in `src/etzhayyim/airshed/contract.cljc` as pure functions,
and every one of them has a negative fixture in
`test/etzhayyim/airshed/contract_test.cljc` — each rule has been shown to fire
on input built to break it, not merely to stay quiet on the committed files.

| Rule | What actually broke |
|---|---|
| `:did/deploy-manifest-match` | manifest `@id` and deploy `actor-did` name different DIDs |
| `:capability/allowlist-beyond-manifest` | the host would be told to allow something never declared |
| `:capability/manifest-beyond-allowlist` | a declared capability dies silently at deploy |
| `:capability/undeclared-step` | a pipeline step calls outside the declared set |
| `:approval/proposal-must-require-human` | an `airshed.propose` step lost `requiresHumanApproval` |
| `:approval/deploy-must-require-approval` | `:deployment/approval-required` is no longer `true` |
| `:approval/actuation-rule-missing` | `RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION` was dropped |
| `:contracts/required-without-lexicon` | deploy requires a contract that ships no lexicon |
| `:contracts/lexicon-not-required` | a lexicon ships but deploy never loads it |
| `:participation/no-withdrawal-path` | `withdrawn` left `engagementLifecycle` |
| `:participation/lifecycle-lexicon-drift` | manifest lifecycle ≠ engagement `:status` enum |
| `:participation/consent-cannot-be-withdrawn` | `withdrawn` left collaborator `consentStatus` |
| `:events/kinds-lexicon-drift` | manifest `eventKinds` ≠ event `:kind` enum |

The three `:approval/*` and three `:participation/*` rules are the machine-
readable face of two promises the README makes in prose:
**the actor proposes and never actuates**, and **participation is opt-in and
always withdrawable**. They are the rules to be most suspicious of when a change
makes them inconvenient.

## What this gate does not check

Being explicit, so a green run is not read as more than it is:

- **It does not check the runtime.** No pipeline is executed, no observation is
  ingested, no proposal is produced. This is descriptor conformance only.
- **It does not reach the network.** `did:web:airshed.etzhayyim.com` is compared
  between two local files; whether that DID document is actually served, and
  whether it lists the right key, is not tested here.
- **It does not check `app-aozora` honours the descriptor.** The manifest's
  `deployment.requires` names `pinned-revision` and `manifest-digest` as host
  obligations; only `did-match` and `capability-allowlist` have a corresponding
  check in this repository.
- **It does not read the README.** The prose and the rules are kept in step by
  review, not by machine.
