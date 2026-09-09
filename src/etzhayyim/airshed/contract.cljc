(ns etzhayyim.airshed.contract
  "airshed actor descriptor の適合規則。**純粋** —— ファイルも network も読まない。

   入力は parse 済みのデータ 3 種:

     manifest   actor-manifest.jsonld 由来。キーは**文字列のまま**扱う
                （keywordize すると \"@id\" / \"@context\" が壊れるので境界で
                変換しない —— cargo の descriptor.cljc と同じ規約）
     deploy     deploy/app-aozora.edn 由来。EDN なのでキーワードキー
     lexicons   lex/*.edn 由来の vector（AT Protocol lexicon 形）

   出力は違反の vector。空なら適合。

   ## この repo の契約は 3 つのファイル面の一致でできている

   deploy/app-aozora.edn は :deployment/requires に \"did-match\" と
   \"capability-allowlist\" を**自分で**挙げている —— つまり『manifest と deploy が
   同じ DID・同じ能力集合を名乗ること』はこの repo 自身が宣言した契約である。
   同様に governance の RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION（提案のみ・実行は
   署名済み council decision）と RULE-AIRSHED-VOLUNTARY-PARTICIPATION（撤回可能な
   opt-in）は README と manifest の両方に書かれている。だがこれまで、その一致を
   検査するものは何も無かった —— cargo が 2026-05〜08 に踏んだ『descriptor が
   静かに割れる』形（@id と did.json が別 DID になっても誰も報告しない）と
   同じ状態だった。ここでは規則を純粋関数として書き、fixture で「規則が実際に
   落ちる」ことを見せてから実ファイルに当てる。"
  (:require [clojure.string :as str]))

(defn- v [rule detail] {:rule rule :detail detail})

;; ── 取り出し ────────────────────────────────────────────────────────────────

(defn manifest-capabilities
  "manifest が宣言する能力の集合（文字列）。"
  [manifest]
  (set (get manifest "capabilities")))

(defn pipeline-steps
  "全 pipeline の全 step。"
  [manifest]
  (for [p (get manifest "pipelines")
        s (get p "steps")]
    s))

(defn lexicon-ids [lexicons] (set (map :id lexicons)))

(defn lex-by-id [lexicons id]
  (first (filter #(= id (:id %)) lexicons)))

(defn property-enum
  "lexicon の :main record の property の enum。無ければ nil。"
  [lex prop]
  (get-in lex [:defs :main :record :properties prop :enum]))

;; ── 規則 ────────────────────────────────────────────────────────────────────

(defn check-did
  "deploy の :deployment/requires が自分で挙げている \"did-match\"。
   manifest の @id と deploy の actor-did が別の DID に割れたら、app-aozora 側の
   did-match 検査は『別人の manifest』を受け入れるか拒否するかのどちらかで、
   どちらでもこの repo の宣言は嘘になっている。"
  [manifest deploy]
  (let [mid (get manifest "@id")
        did (:deployment/actor-did deploy)]
    (when (not= mid did)
      [(v :did/deploy-manifest-match
          (str "manifest @id " (pr-str mid) " ≠ deploy actor-did " (pr-str did)))])))

(defn check-allowlist
  "\"capability-allowlist\"（deploy 自身が requires に挙げている）。両方向に見る:
   allowlist が manifest より広ければ『宣言していない権限を配る』、狭ければ
   『宣言した能力が配備で黙って死ぬ』—— どちらも violation。"
  [manifest deploy]
  (let [caps  (manifest-capabilities manifest)
        allow (set (map name (:deployment/capability-allowlist deploy)))]
    (concat
     (for [c (sort (remove caps allow))]
       (v :capability/allowlist-beyond-manifest
          (str "deploy allowlist has " c " which the manifest never declares")))
     (for [c (sort (remove allow caps))]
       (v :capability/manifest-beyond-allowlist
          (str "manifest declares " c " which the deploy allowlist does not carry"))))))

(defn check-steps
  "deny-by-default: pipeline の step は宣言済み capability しか呼べない。
   ここが素通りすると『宣言にない権限で動く actor』が作れる（cargo の
   undeclared-capability 規則と同じ理由）。"
  [manifest]
  (let [caps (manifest-capabilities manifest)]
    (for [s (pipeline-steps manifest)
          :let [f (get s "fn")]
          :when (and f (not (contains? caps f)))]
      (v :capability/undeclared-step
         (str "step " (pr-str (get s "id")) " calls " (pr-str f)
              " outside the declared capabilities")))))

(defn check-approval
  "RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION の機械可読な面。
   (1) airshed.propose を呼ぶ step は requiresHumanApproval=true を持つ
   (2) deploy は :deployment/approval-required true
   (3) governance rules にその rule id 自体が残っている"
  [manifest deploy]
  (concat
   (for [s (pipeline-steps manifest)
         :when (= "airshed.propose" (get s "fn"))
         :when (not (true? (get-in s ["args" "requiresHumanApproval"])))]
     (v :approval/proposal-must-require-human
        (str "step " (pr-str (get s "id"))
             " calls airshed.propose without requiresHumanApproval=true")))
   (when-not (true? (:deployment/approval-required deploy))
     [(v :approval/deploy-must-require-approval
         ":deployment/approval-required is not true")])
   (let [ids (set (map #(get % "id") (get-in manifest ["governance" "rules"])))]
     (when-not (contains? ids "RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION")
       [(v :approval/actuation-rule-missing
           "governance rules no longer carry RULE-AIRSHED-NO-AUTONOMOUS-ACTUATION")]))))

(defn check-contracts
  "deploy の :deployment/required-contracts と lex/ の中身の全単射。
   required なのに lexicon が無ければ配備が壊れ、lexicon が在るのに required に
   無ければそのコレクションは黙って配備から落ちる —— 両方向 violation。"
  [deploy lexicons]
  (let [required (set (:deployment/required-contracts deploy))
        shipped  (lexicon-ids lexicons)]
    (concat
     (for [c (sort (remove shipped required))]
       (v :contracts/required-without-lexicon
          (str c " is required by the deploy descriptor but ships no lexicon")))
     (for [c (sort (remove required shipped))]
       (v :contracts/lexicon-not-required
          (str c " ships a lexicon the deploy descriptor never requires"))))))

(defn check-withdrawal
  "RULE-AIRSHED-VOLUNTARY-PARTICIPATION の機械可読な面。README は『withdrawal
   path を持つ』と 3 箇所で言っている:
   (1) manifest の engagementLifecycle に \"withdrawn\" が在る
   (2) engagement lexicon の status enum が lifecycle と**一致**する
       （どちらか片方だけ直すのが一番起こりやすい壊れ方）
   (3) collaborator lexicon の consentStatus に \"withdrawn\" が在る
   lexicon 自体の不在は check-contracts が報告するので、ここでは在るものだけ見る。"
  [manifest lexicons]
  (let [lifecycle  (set (get-in manifest ["collaboration" "engagementLifecycle"]))
        engagement (lex-by-id lexicons "com.etzhayyim.airshed.engagement")
        collab     (lex-by-id lexicons "com.etzhayyim.airshed.collaborator")]
    (concat
     (when-not (contains? lifecycle "withdrawn")
       [(v :participation/no-withdrawal-path
           "engagementLifecycle has no \"withdrawn\" state")])
     (when engagement
       (let [status (set (property-enum engagement :status))]
         (when (not= lifecycle status)
           [(v :participation/lifecycle-lexicon-drift
               (str "manifest lifecycle " (pr-str (sort lifecycle))
                    " ≠ engagement status enum " (pr-str (sort status))))])))
     (when collab
       (let [consent (set (property-enum collab :consentStatus))]
         (when-not (contains? consent "withdrawn")
           [(v :participation/consent-cannot-be-withdrawn
               "collaborator consentStatus enum has no \"withdrawn\"")]))))))

(defn check-event-kinds
  "manifest の collaboration.eventKinds と event lexicon の kind enum の一致。
   片側だけに kind を足すと、その種別のイベントは manifest 上は開けるのに
   record が書けない（またはその逆）。"
  [manifest lexicons]
  (let [kinds (set (get-in manifest ["collaboration" "eventKinds"]))
        event (lex-by-id lexicons "com.etzhayyim.airshed.event")]
    (when event
      (let [enum (set (property-enum event :kind))]
        (when (not= kinds enum)
          [(v :events/kinds-lexicon-drift
              (str "manifest eventKinds " (pr-str (sort kinds))
                   " ≠ event kind enum " (pr-str (sort enum))))])))))

(defn check
  "全規則。空 vector なら適合。"
  [manifest deploy lexicons]
  (vec (concat (check-did manifest deploy)
               (check-allowlist manifest deploy)
               (check-steps manifest)
               (check-approval manifest deploy)
               (check-contracts deploy lexicons)
               (check-withdrawal manifest lexicons)
               (check-event-kinds manifest lexicons))))
