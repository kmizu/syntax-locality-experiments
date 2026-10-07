# Syntax locality experiments

**初めて与えられた言語の文法に従うプログラム生成で、冗長な構文がAIの正確さを高めるか**を調べる実験。たとえば`def foo ... end def`や`def foo ... end def foo`のように、終端にも種類や名前を置くことで、その場の情報を増やし、長く離れた文脈への依存を減らせるのではないか、という仮説を検討する。内部の文脈依存や記憶を直接測定する実験ではなく、生成結果の正確さを測る。

同じASTを、`}`、`end`、`end func`、`end func n_abcdefgh`、情報を持たない長さ合わせの終端という5条件で表す。識別子、値、意味、例題の内容と順番、語彙対応は固定する。単に長い出力になる負担もあるので、改善・差なし・悪化のいずれも結果として扱う。

文法はプロンプトで明示する。「学習データに一度も存在しなかった言語」と証明した実験や、例だけから文法を推論する実験ではない。読解・全体の解析は生成との関係を見る補助課題で、別々に集計する。

[HTMLレポート](https://kmizu.github.io/syntax-locality-experiments/) · [保存された報告データ](experiments/data/) · [実験仕様](scala3-syntax-locality-icl-experiment.md) · [プロトコル](docs/protocol.md) · [解釈上の限界](docs/limitations.md)

## ソースと成果物

ソース一式を通常のファイルとして置いている。clone後にZIPを展開する必要はない。

| 場所 | 内容 |
| --- | --- |
| `bench/` | AST、文法、生成器、プロンプト、実行管理、厳密採点、統計、Markdown/CSV報告 |
| `llm-core/` | JDK HTTPクライアント、JSON、OpenAI Responses API接続 |
| `configs/`、`docs/` | 実験設定、プロトコル、設計と制約 |
| `reports/` | 保存済み報告をHTMLにする独立したScalaアプリ |
| `tools/` | P2と同じ16,384出力token上限を確認する独立helperとoffline fixture tests |
| `experiments/data/` | 公開対象の保存済み報告、設定、検証証跡 |
| `experiments/site/` | 生成済みHTML、ダウンロード用データ、生成時の来歴 |
| `experiments/p1-main/source/` | 凍結済み読解mainに対応する旧実装 |
| `experiments/archives/` | 旧配布ZIP。補助的な履歴資料 |

アプリケーションの依存はScala標準ライブラリとJDK APIだけ。外部SDK、HTTP/JSON/parser/statisticsライブラリやPython/Nodeでの実行は使わない。ビルドツールとコンパイラのダウンロードは必要になる。

## 環境を用意する

Windows、macOS、Linuxで、**JDK 21、sbt 1.10.7、Scala 3.3.8**を使う。

1. [Eclipse Temurinの公式配布](https://adoptium.net/temurin/releases/?version=21)から、OSとCPUに合う**JDK 21**をインストールする。`JAVA_HOME`と`PATH`がそのJDKを指すように設定する。
2. [sbt公式のOS別インストール手順](https://www.scala-sbt.org/1.x/docs/Setup.html)を参照し、[sbt 1.10.7の公式リリース](https://github.com/sbt/sbt/releases/tag/v1.10.7)をインストールする。WindowsはMSI、macOS/LinuxはZIPまたはtgzの`bin`を`PATH`に追加できる。
3. このリポジトリをcloneして、そのディレクトリへ移動する。Scala 3.3.8は`build.sbt`、sbt 1.10.7は`project/build.properties`で固定してあり、sbtが取得する。

```sh
git clone https://github.com/kmizu/syntax-locality-experiments.git
cd syntax-locality-experiments
java -version
sbt "show scalaVersion" "show sbtVersion"
sbt test
sbt "bench / run --help"
```

`java -version`が21、sbtの起動表示が1.10.7であることを確認する。以下の`sh`形式のコマンドは通常のsbtを使う。PowerShellは前提ではない。既存Windows環境向けの`./scripts/sbt.ps1`は任意の補助ラッパー。

## 実験の状態

各課題・プロトコルを混ぜずに読む。

| 実験 | 内容と保存された状態 |
| --- | --- |
| P1 pilot | 1,800件の実モデル試行を完了、strict correctは1,622件。生成を含む探索的結果。prefixのscope lookup / active stackは全プログラムの解析ではない |
| 凍結済みP1 main | 読解`scope_lookup / after_close`の別課題。2026-10-07の再中断後の保存済み採点：計画5,120件、送信2,748件、terminal2,753件、評価可能2,688件、correct2,331件。infra missing60件、未送信2,372件。途中結果のCIは未計算 |
| P2 | 全文の`ast_to_source`生成と`source_to_ast`解析。計画960件のうち送信519件、terminal521件、評価可能512件、correct348件、API拒否7件、未送信441件。生成168/268、解析180/244を別表で表示。途中結果のCIは未計算 |

2026-10-06の残高拒否後、2026-10-07（UTC）の入金申告と実通信probe成功を確認して同じ計画を継続した。その後APIが再び`credit_balance_exhausted`を返したため、MainとP2を停止した。全計画の実行は完了していない。正解率・有意差による停止ではない。保存した応答は全文で厳密採点し、terminalを再送していない。P2の入力は閉じ終わりまである全文。元のMainはprobeまでのprefix読解なので、全文解析には含めない。[現在の実行経過](experiments/data/supporting/live-recovery-20261007.txt)と[以前の中断記録](experiments/data/supporting/live-interruption.txt)を区別する。自動課金の操作は行っていない。

Mainでは2,688件の回答を採点し、59件のAPI拒否、元の結果不明1件、未送信terminal5件を保持する。terminalがない2,367件にはcount-only2件と未着手2,365件を含み、報告上の未送信2,372件はそれらと未送信terminal5件の合計。未確定予約は61件・707,127 tokensで、旧60件・692,889 tokensをすべて保持する。P2は未確定予約7件・166,039 tokens、count-only待ち1件、未着手438件。使用量不明をゼロへ置き換えない。P2生成のD（種類＋名前）は52/53正解、C（種類）は34/54、B（汎用end）は25/54だが、途中の探索的結果として読む。

P1の生成結果は、閉じラベルの生成への効果を見る探索的pilotの結果。既存mainの主比較は読解のD−Bで、生成の主比較へ読み替えない。P2は48構造family、深さ`[2,4,8,16]`、filler`[0,8,32]`、2方向×2語彙×5構文の別pilot。[P2の設計と検証状態](docs/full-program-structures.md)と[HTMLの生成・解析別表](https://kmizu.github.io/syntax-locality-experiments/)を参照。保存済みP2 reportとCSVから厳密採点結果を表示し、execution snapshot、synthetic mock、P1、Mainと合算しない。

通常のルート実装のproduction source hashは`41ffc2647b8f3c3838a17c2425b0be97007fe611f5d0a68d3dd9d1c79e1b0b96`。旧mainの実装は`1d041fa5f4ae2a499b89151fb99958312a49b883a6315adad4d65e63307edca1`を保存している。凍結済みmainの再開には、その旧実装と元の保存済みrun、能力確認、凍結hashを使う。ルートのP2実装で旧mainを再開しない。

## APIを使わずに確認する

新しいrunディレクトリを使う。計画、audit、mock、採点、報告はAPI key不要でHTTPを送らない。

```sh
sbt "bench / run plan --preset pilot --phase p2 --out runs/p2-offline --config configs/full-program-pilot.json --model gpt-5.6-terra"
sbt "bench / run audit --run runs/p2-offline"
sbt "bench / run run --run runs/p2-offline"
sbt "bench / run run --run runs/p2-offline --mock"
sbt "bench / run score --run runs/p2-offline"
sbt "bench / run report --run runs/p2-offline"
```

`run`だけなら実行計画を表示する。`--mock`はsynthetic fixtureを使い、`synthetic_mock=true`を保存する。mockの正答率は実モデル性能ではない。出力は`report.md`、`summary.csv`、`paired-comparisons.csv`、`errors.csv`、`scores.jsonl`。元のリクエスト、応答、attempt、usage、goldを保存するrunはローカルの`runs/`に置き、通常のgit対象にはしない。

## 明示的にAPIを実行する場合

通常のビルドとHTML生成にAPI keyは不要。実行するときだけ、利用するシェルの環境変数`OPENAI_API_KEY`を設定する。任意で`OPENAI_PROJECT`、`OPENAI_ORGANIZATION`も設定できる。CLIは環境変数を読むが、`.env`を自動ロードしない。keyや`.env`をgitに含めない。

以下は**新規の有料利用を伴いうるopt-in例**で、実行済みの記録ではない。無料quotaや利用料金は仮定しない。通常のlive runはgeneration・HTTP・tokenの3つの数値上限をすべて要求する。

```sh
sbt "bench / run plan --preset pilot --phase p2 --out runs/p2-live --config configs/full-program-pilot.json --model gpt-5.6-terra"
sbt "bench / run audit --run runs/p2-live"
```

標準CLIの`preflight`は出力上限8,192で、P2の16,384とは一致しない。P2の前には[`tools/P2CapabilityProbe.scala`](tools/P2CapabilityProbe.scala)を使う。リポジトリのルートで`sbt`を起動し、対話プロンプトに以下を入力する。最初の実行は計画表示だけでHTTPは0件。その後の`--execute`を付けた実行だけが、count＋generationの最大2 HTTP、generation 1件、20,000 tokenのローカル上限で新しいprobeを行う。

```text
set bench / Test / unmanagedSources += file("tools/P2CapabilityProbe.scala")
bench / Test / runMain locality.bench.verification.P2CapabilityProbe --run runs/p2-live --out runs/p2-preflight
bench / Test / runMain locality.bench.verification.P2CapabilityProbe --run runs/p2-live --out runs/p2-preflight --execute --max-http-attempts 2 --local-token-cap 20000
exit
```

`runs/p2-preflight/capabilities.json`の成功、model=`gpt-5.6-terra`、effort=`low`、上限16,384、sourceHashとprotocolHashの一致を確認してから、以下へ進む。probe出力は毎回新しいディレクトリを使い、実験runとは別に保存する。tiny requestの能力確認は実験の成功率を測らない。**P2 pilotの通常runnerには、この能力確認を自動で必須にするgateがまだない**ため、実行者が確認を省略しない。

```sh
sbt "bench / run run --run runs/p2-live --execute --max-generation-calls 20 --max-http-attempts 80 --local-token-cap 1000000"
sbt "bench / run resume --run runs/p2-live --execute --max-generation-calls 20 --max-http-attempts 80 --local-token-cap 1000000"
sbt "bench / run score --run runs/p2-live"
sbt "bench / run report --run runs/p2-live"
```

この例は1回に最大20件の追加generationで、960件の全体完了を意味しない。generation/HTTP上限はその呼出しでの追加作業、token上限はrunごとの保存済みUTC日付budget windowの累積上限。別runは別台帳なので、preflight等も合わせて総使用量を管理する。usage不明の予約は0として扱わず残す。

helperだけをAPIなしで検証するには、別の`sbt`対話セッションで以下を入力する。6ケースはsynthetic fixtureだけを使い、live HTTPとモデル呼出しは0件。helperは一時的にTest sourceへ追加するもので、productionのsource hashや通常のbuild設定を変更しない。

```text
set bench / Test / unmanagedSources ++= Seq(file("tools/P2CapabilityProbe.scala"), file("tools/P2CapabilityProbeTests.scala"))
bench / Test / compile
bench / Test / runMain locality.bench.verification.P2CapabilityProbeTests
exit
```

公開helperは検証済みhelperから自己hashの参照パスだけを`verification/`から`tools/`へ変更した。historical live probeのhelperHashを公開helperのhashへ読み替えず、保存されたprobe来歴を保持する。historical helperのSHA-256は`f51e2a170646617e08bbe67b23a5565abcdaf446eeab1ac9d02e75c2af38641d`、この公開helperは`87635131a6d386d960d8a500e6e0bc0496ba39a944b6dbd31e917f0abee2f5da`。公開用checkoutの検証ではliveモデル呼出し0件で、6ケースとdry-runのみを行った。

terminal trialは誤答・refusal・incompleteも再送しない。resumeは未完了の対象だけを扱う。許されたインフラ障害のみretry可能で、誤答をretryしたり、best-of-Nを選んだり、条件別にpromptを改善したりしない。モデル・prompt・ソース・設定変更は新規planに分ける。P2 mainのfreeze/liveは現状未対応で、別の確証計画が必要。

## HTMLを再生成する

保存済み採点済み報告からHTMLを作る。モデル呼出し、再採点、bootstrapの再計算はしない。

```sh
cd reports
sbt "run ../experiments/data ../experiments/site"
cd ..
```

生成物は[`experiments/site/index.html`](experiments/site/index.html)。数値は保存済みCSVから読み、CIは保存済み`paired-comparisons.csv`をそのまま使う。入力データのコピー、SHA-256一覧、`build-provenance.json`も生成する。詳細は[`reports/README.md`](reports/README.md)へ。公開データは選択された保存済み報告と証跡であり、全live trialの再採点には元の完全なrun artifactsが必要になる。

GitHub Actionsは通常のソースをcheckoutしてルートで`sbt test`を実行し、`reports/`でHTMLを2回生成して一致を確認し、`experiments/site/`をGitHub Pagesに配置する。別のP2 workflowも明示的な`--mock`だけを実行する。API key、live実行、ZIPの展開はこの公開手順に不要。

## 結果の読み方

同じ構造の5つの表記、語彙違い、replicateは独立サンプルではなく、構造familyがsampling cluster。生成は完全な構文解析とAST一致で厳密に採点し、形式違反、構文違反、構文は正しいがASTが違う応答を区別する。モデル失敗、インフラmissingness、未実行も分ける。

測るのは、指定された文法での生成・読解・解析の正確さ。局所の情報が長距離の文脈への依存を減らすかは検討する仮説で、正答率の差だけから内部の機構を確定しない。生成・読解・解析の絶対正答率を同じ難しさの尺度として比較せず、token量やlatencyを内部の認知負荷、attention量、FLOPの実測値とも呼ばない。有限のテストとmock成功は実装検証であり、仮説支持の実測とは区別する。
