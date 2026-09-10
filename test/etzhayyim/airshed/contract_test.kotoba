(ns etzhayyim.airshed.contract-test
  "規則が**実際に落ちる**ことを fixture で見せる。

  repo-test が『実物が規則を通ること』を見るのに対し、こちらは『規則が壊れた
  入力で本当に violation を返すこと』を見る。この向きのテストが無いと、規則の
  実装を骨抜きにしても（:when に false を挟むなど）実物は緑のままなので、
  誰も気づかない —— scripts/maturity-loop の mutation はまさにそこを撃つ。"
  (:require [clojure.test :refer [deftest is testing]]
            [etzhayyim.airshed.contract :as c]))

;; ── fixture ─────────────────────────────────────────────────────────────────
;; 実物と同型の最小 descriptor。fixture 自身が check を通ることを最初に固定する
;; —— 通らない fixture の上の「落ちるテスト」は何も証明しない。

(def manifest
  {"@id" "did:web:fixture.example.com"
   "capabilities" ["airshed.observe" "airshed.propose"]
   "governance" {"rules" [{"id" "RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION"}]}
   "pipelines" [{"steps" [{"id" "obs" "fn" "airshed.observe"}
                          {"id" "draft" "fn" "airshed.propose"
                           "args" {"requiresHumanApproval" true}}]}]
   "collaboration" {"engagementLifecycle" ["draft" "withdrawn"]
                    "eventKinds" ["listening-session"]}})

(def deploy
  {:deployment/actor-did "did:web:fixture.example.com"
   :deployment/approval-required true
   :deployment/capability-allowlist [:airshed.observe :airshed.propose]
   :deployment/required-contracts ["com.etzhayyim.airshed.collaborator"
                                   "com.etzhayyim.airshed.engagement"
                                   "com.etzhayyim.airshed.event"]})

(def lexicons
  [{:lexicon 1 :id "com.etzhayyim.airshed.collaborator"
    :defs {:main {:type "record"
                  :record {:type "object"
                           :properties {:consentStatus {:type "string"
                                                        :enum ["opted-in" "withdrawn"]}}}}}}
   {:lexicon 1 :id "com.etzhayyim.airshed.engagement"
    :defs {:main {:type "record"
                  :record {:type "object"
                           :properties {:status {:type "string"
                                                 :enum ["draft" "withdrawn"]}}}}}}
   {:lexicon 1 :id "com.etzhayyim.airshed.event"
    :defs {:main {:type "record"
                  :record {:type "object"
                           :properties {:kind {:type "string"
                                               :enum ["listening-session"]}}}}}}])

(defn- rules-of [violations] (set (map :rule violations)))

;; ── 緑の側 ──────────────────────────────────────────────────────────────────

(deftest the-fixture-descriptor-passes
  (is (= [] (c/check manifest deploy lexicons))))

;; ── 落ちる側（規則 1 つにつき最低 1 つ）────────────────────────────────────

(deftest a-deploy-did-that-differs-from-the-manifest-is-a-violation
  (let [broken (assoc deploy :deployment/actor-did "did:web:other.example.com")]
    (is (contains? (rules-of (c/check manifest broken lexicons))
                   :did/deploy-manifest-match))))

(deftest an-allowlist-wider-than-the-manifest-is-a-violation
  (let [broken (update deploy :deployment/capability-allowlist conj :airshed.actuate)]
    (is (contains? (rules-of (c/check manifest broken lexicons))
                   :capability/allowlist-beyond-manifest))))

(deftest a-capability-missing-from-the-allowlist-is-a-violation
  (let [broken (assoc deploy :deployment/capability-allowlist [:airshed.observe])]
    (is (contains? (rules-of (c/check manifest broken lexicons))
                   :capability/manifest-beyond-allowlist))))

(deftest a-step-that-calls-an-undeclared-capability-is-a-violation
  (let [broken (assoc-in manifest ["pipelines" 0 "steps" 0 "fn"] "custom")]
    (is (contains? (rules-of (c/check broken deploy lexicons))
                   :capability/undeclared-step))))

(deftest a-proposal-without-human-approval-is-a-violation
  (let [broken (assoc-in manifest
                         ["pipelines" 0 "steps" 1 "args" "requiresHumanApproval"]
                         false)]
    (is (contains? (rules-of (c/check broken deploy lexicons))
                   :approval/proposal-must-require-human))))

(deftest a-deploy-that-drops-approval-is-a-violation
  (let [broken (assoc deploy :deployment/approval-required false)]
    (is (contains? (rules-of (c/check manifest broken lexicons))
                   :approval/deploy-must-require-approval))))

(deftest dropping-the-actuation-rule-is-a-violation
  (let [broken (assoc-in manifest ["governance" "rules"] [])]
    (is (contains? (rules-of (c/check broken deploy lexicons))
                   :approval/actuation-rule-missing))))

(deftest a-required-contract-without-a-lexicon-is-a-violation
  (let [broken (update deploy :deployment/required-contracts conj
                       "com.etzhayyim.airshed.missing")]
    (is (contains? (rules-of (c/check manifest broken lexicons))
                   :contracts/required-without-lexicon))))

(deftest a-lexicon-the-deploy-never-requires-is-a-violation
  (let [broken (assoc deploy :deployment/required-contracts
                      ["com.etzhayyim.airshed.collaborator"
                       "com.etzhayyim.airshed.engagement"])]
    (is (contains? (rules-of (c/check manifest broken lexicons))
                   :contracts/lexicon-not-required))))

(deftest a-lifecycle-without-a-withdrawal-path-is-a-violation
  (let [broken (assoc-in manifest ["collaboration" "engagementLifecycle"] ["draft"])]
    (testing "lifecycle 側から withdrawn が消えると 2 つの規則が同時に落ちる"
      (let [rules (rules-of (c/check broken deploy lexicons))]
        (is (contains? rules :participation/no-withdrawal-path))
        (is (contains? rules :participation/lifecycle-lexicon-drift))))))

(deftest an-engagement-enum-that-drifts-from-the-lifecycle-is-a-violation
  (let [broken (mapv (fn [lex]
                       (if (= "com.etzhayyim.airshed.engagement" (:id lex))
                         (assoc-in lex [:defs :main :record :properties :status :enum]
                                   ["draft"])
                         lex))
                     lexicons)]
    (is (contains? (rules-of (c/check manifest deploy broken))
                   :participation/lifecycle-lexicon-drift))))

(deftest a-consent-that-cannot-be-withdrawn-is-a-violation
  (let [broken (mapv (fn [lex]
                       (if (= "com.etzhayyim.airshed.collaborator" (:id lex))
                         (assoc-in lex [:defs :main :record :properties :consentStatus :enum]
                                   ["opted-in"])
                         lex))
                     lexicons)]
    (is (contains? (rules-of (c/check manifest deploy broken))
                   :participation/consent-cannot-be-withdrawn))))

(deftest event-kinds-that-drift-from-the-lexicon-are-a-violation
  (let [broken (assoc-in manifest ["collaboration" "eventKinds"]
                         ["listening-session" "rally"])]
    (is (contains? (rules-of (c/check broken deploy lexicons))
                   :events/kinds-lexicon-drift))))
