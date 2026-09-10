(ns etzhayyim.airshed.repo-test
  "**この repo に実際に commit されているファイル**を検査する。

  contract-test が『規則が落ちること』を fixture で見せるのに対し、こちらは
  『実物がその規則を通ること』を見る。deploy/app-aozora.edn が
  :deployment/requires に自分で挙げている did-match / capability-allowlist を
  含め、manifest ↔ deploy ↔ lex/ の 3 面一致を固定する。"
  (:require [kotoba.lang.text :as str]
            [clojure.test :refer [deftest is testing]]
            [cljs.reader :as reader]
            [etzhayyim.airshed.contract :as c]
            ["node:fs" :as fs]
            ["node:path" :as path]))

(def repo-root (.cwd js/process))

(defn- slurp* [rel] (.readFileSync fs (path/join repo-root rel) "utf8"))

(def manifest (js->clj (js/JSON.parse (slurp* "actor-manifest.jsonld"))))
(def deploy (reader/read-string (slurp* "deploy/app-aozora.edn")))
(def lexicons
  (->> (.readdirSync fs (path/join repo-root "lex"))
       (filter #(str/ends-with? % ".edn"))
       (mapv #(reader/read-string (slurp* (str "lex/" %))))))

;; ── 全規則 ──────────────────────────────────────────────────────────────────

(deftest the-committed-descriptor-has-no-violations
  (let [violations (c/check manifest deploy lexicons)]
    (is (= [] violations)
        (str "違反 " (count violations) " 件:\n"
             (str/join "\n" (map #(str "  " (:rule %) " — " (:detail %)) violations))))))

;; ── 個別（落ちたとき、どの面が割れたかを名指しするための細分）──────────────

(deftest deploy-and-manifest-name-the-same-did
  (is (= (get manifest "@id") (:deployment/actor-did deploy))))

(deftest deploy-allowlist-equals-manifest-capabilities
  (is (= (set (get manifest "capabilities"))
         (set (map name (:deployment/capability-allowlist deploy))))))

(deftest every-pipeline-step-calls-a-declared-capability
  (let [caps (c/manifest-capabilities manifest)]
    (doseq [s (c/pipeline-steps manifest)]
      (is (contains? caps (get s "fn"))
          (str "step " (get s "id") " calls " (get s "fn"))))))

(deftest a-proposal-cannot-skip-human-approval
  (testing "airshed.propose を呼ぶ step は必ず requiresHumanApproval=true"
    (let [propose-steps (filter #(= "airshed.propose" (get % "fn"))
                                (c/pipeline-steps manifest))]
      (is (seq propose-steps) "propose step が 1 つも無いなら、この検査は空虚")
      (doseq [s propose-steps]
        (is (true? (get-in s ["args" "requiresHumanApproval"]))
            (str "step " (get s "id"))))))
  (testing "deploy 側も approval-required"
    (is (true? (:deployment/approval-required deploy))))
  (testing "governance に RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION が残っている"
    (is (contains? (set (map #(get % "id") (get-in manifest ["governance" "rules"])))
                   "RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION"))))

(deftest every-required-contract-ships-a-lexicon
  (is (= (set (:deployment/required-contracts deploy))
         (c/lexicon-ids lexicons))))

(deftest an-engagement-can-always-be-withdrawn
  (testing "engagement lexicon の status enum"
    (is (some #{"withdrawn"}
              (c/property-enum (c/lex-by-id lexicons "com.etzhayyim.airshed.engagement")
                               :status))))
  (testing "manifest の engagementLifecycle"
    (is (some #{"withdrawn"} (get-in manifest ["collaboration" "engagementLifecycle"]))))
  (testing "collaborator の consentStatus"
    (is (some #{"withdrawn"}
              (c/property-enum (c/lex-by-id lexicons "com.etzhayyim.airshed.collaborator")
                               :consentStatus)))))

(deftest manifest-lifecycle-matches-the-engagement-lexicon
  (is (= (set (get-in manifest ["collaboration" "engagementLifecycle"]))
         (set (c/property-enum (c/lex-by-id lexicons "com.etzhayyim.airshed.engagement")
                               :status)))))

(deftest event-kinds-match-the-event-lexicon
  (is (= (set (get-in manifest ["collaboration" "eventKinds"]))
         (set (c/property-enum (c/lex-by-id lexicons "com.etzhayyim.airshed.event")
                               :kind)))))
