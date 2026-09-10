#!/usr/bin/env nbb
;; run_tests.cljs — airshed actor contract の検査。
;;
;;   nbb --classpath src:test run_tests.cljs
;;
;; deploy/app-aozora.edn は :deployment/requires に did-match と
;; capability-allowlist を自分で挙げているが、2026-08-22 までこの repo には
;; その一致を検査するものが何も無かった。actor-manifest.jsonld ↔
;; deploy/app-aozora.edn ↔ lex/*.edn の 3 面一致をここで固定する。
;; workspace の規則で script host は nbb（.ts / .mjs / .sh の新規作成は禁止）。
(ns run-tests
  (:require [clojure.test :as t]
            [etzhayyim.airshed.contract-test]
            [etzhayyim.airshed.repo-test]))

(def green-marker
  "scripts/maturity-loop/mutations.edn の `:green-marker`。
  全部緑のときだけ出る —— 出力に現れるかどうかで mutation が噛んだかを判定する
  ので、緑でないときに印字してはならない。"
  "airshed actor contract: all green")

(defmethod t/report [:cljs.test/default :end-run-tests] [m]
  (if (t/successful? m)
    (println (str "\n" green-marker))
    (do (println "\nairshed actor contract: FAILED")
        (js/process.exit 1))))

(t/run-tests 'etzhayyim.airshed.contract-test
             'etzhayyim.airshed.repo-test)
