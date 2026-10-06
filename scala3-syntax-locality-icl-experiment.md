# Syntax Locality ICL Bench — Scala 3 実験・実装指示書

> Claude Code / Codex に、この文書全体を実装指示書として渡す。
> この文書は実験計画であり、実験結果ではない。仮説を支持する数値を捏造しない。
> 最初に P0 を完成させる。外部 API キーなしでもテスト・問題生成・模擬実行・集計まで動かす。
> 実 API 実行は `--execute` と明示された利用上限を必要とする。主実験は pilot と設定凍結の後に行う。

**Goal:** 終端に構文カテゴリや識別子を繰り返す文法が、LLM の構造理解・スコープ解決・構文生成に与える効果を、同じ構造を異なる表記にした対応付き実験で調べる。

**Architecture:** 独立した AST オラクル、5 種類の renderer / parser、few-shot プロンプト生成器、自作の OpenAI Responses API クライアント、再開可能な実験 runner、決定的な採点・統計処理を組み合わせる。LLM を採点者に使わない。

**Tech Stack:** Scala 3.3.8、JDK 21、sbt 1.10.7。アプリケーションの外部ライブラリ依存はゼロとし、Scala 標準ライブラリと JDK だけで構築する。LLM 呼び出し用ライブラリはリポジトリ内の `llm-core` に実装する。

**Spec:** この文書の第 1〜12 節を仕様、第 13 節を実装タスク、第 14〜16 節を実行手順・受け入れ条件・参照資料とする。

**初期対象モデル:** `gpt-5.6-terra`。別モデルへの自動フォールバックは禁止。API の実仕様は実装開始時にも確認する。

**作成日:** 2026-10-05。モデル・API・データ共有制度については、この日の公式資料を参照した。

## Global Constraints

- 実験、API クライアント、採点、統計、レポート生成は Scala 3 で実装する。
- OpenAI SDK、LangChain、LangChain4j、外部 HTTP / JSON / parser / 統計ライブラリを使わない。Python / Node.js プロセスへの委譲もしない。
- JDK の `java.net.http.HttpClient`、ファイル API、暗号学的 hash、並行処理 API は使用できる。ビルドツールと Scala コンパイラの取得は、この依存禁止の対象外とする。
- 中核比較では意味、識別子、値、AST、例示の内容と順序を揃え、表記だけを変える。
- 「名前付き終端のほうが優れている」を前提に実装・選別・分析しない。効果なし、悪化、読み取りと生成の逆転も正当な結果である。
- API エラーとモデルの誤答を区別する。誤答を消すための retry、best-of-N、条件別のプロンプト修正は禁止。
- API キー、組織の機密情報、実在の社内コードをデータセットやログへ入れない。合成データだけを送信する。
- プロンプト・生成器・採点器・設定に version と hash を持たせ、変更後の結果を変更前と無条件に合算しない。

## Review Focus

特に次の失敗をテストで防ぐ。

1. `end` と `end func name` で異なる情報量の回答を要求し、それをそのまま正答率比較する。
2. renderer とオラクルの同じバグを共有し、誤った正解を全条件へ配る。
3. Responses API の `output[0]` を本文と決めつけ、推論項目や refusal、incomplete を誤読する。
4. retry、通信断、再開、キャッシュにより、同一試行を二重集計したり利用量を過小計上する。
5. 同じ AST の表記違い・字句置換・再サンプリングを、独立した標本として水増しする。

---

## 1. 何を検証するか

### 1.1 出発点

検討したいのは、次のような冗長な終端である。

```text
class Person
  fun age
    return 43
  end fun age
end class Person
```

開始位置にしかなかった情報を終端にも置けば、読み手が終端近辺から構造を再確認しやすくなる可能性がある。しかし、その可能性と「生成が簡単になる」は別である。

### 1.2 最初から分ける三つの問い

**H1: 読み取りへの効果。** すでに書かれているラベル付き終端を読んだ後、現在のスコープや有効な変数束縛を正しく把握しやすくなるか。

**H2: 生成への効果。** 同一 AST を出力する際、ラベル付き終端により構造の欠落・余分な閉鎖・取り違えが減るか。識別子の再出力という追加負担で、逆に失敗が増える可能性もある。

**H3: 冗長性の性質。** 改善がある場合、単に長い区切りを置いた効果か、それとも対応する種類・名前を繰り返した効果か。

主評価は H1。H2 は別タスクで評価し、H3 は情報を含まない長い終端を対照として調べる。

### 1.3 以前の簡単な案から修正する点

次の比較は主実験にしない。

```text
A の空欄の正解: end
B の空欄の正解: end func n_qazwsxed
```

A は定数を出せば正解になり、B は遠い名前まで再現する必要がある。課題の要求情報量が違うため、直接の正答率比較は不公平である。

また、`end func name` は**それを書く時点では開始側の名前に対する遠距離依存を残す**。ラベルが生成前から局所的に存在するわけではない。終端を読んだ後の利用と、終端そのものの生成を混同しない。

キーワードをランダム化しても、事前学習・tokenization・既存構文への類似の影響を消せるわけではない。「影響を弱める対照条件」と表現し、純粋に文法だけを測ったとは主張しない。

API の正答率・出力トークン数・推論トークン数・遅延から、モデル内部の「認知負荷」や実 FLOPs を直接測定したとは言わない。観測可能な課題性能と利用コストを報告する。

## 2. 実装範囲と到達順序

### P0: 最小の実験系

自作 JSON / HTTP / Responses client、AST と独立オラクル、5 表記、スコープ解決タスク、固定 few-shot、再開可能な runner、対応付き正答率比較、Markdown / CSV 出力を作る。

最初の live smoke は 20 生成リクエスト。P0 pilot は 720 生成リクエストの計画を生成するが、実行上限内で分割してよい。

### P1: 読み取りと生成の切り分け

active-stack タスク、AST-to-source タスク、完全な 1,800 リクエストの pilot、クラスタ bootstrap、主実験の設定凍結・検証を追加する。

### P2: 効果を見てからの追加実験

インデント、例示数、同一種類だけのネスト、推論 effort、修復、切り出し窓、入力長のマッチングなど。第 12 節に設計上の注意を記す。P0 / P1 を遅らせるために先行実装しない。

今回作るのは実験用 CLI である。Web UI、MCP サーバー、分散実行基盤、汎用エージェント、複数 provider の巨大な抽象化、学習基盤は作らない。

## 3. 比較するミニ言語

### 3.1 共通の意味

現実の Scala / Java / Ruby を比較するのではなく、**同じ意味を持つ小さなスコープ言語**を 5 種類の表記にする。

構文カテゴリは `unit`、`func`、`area` の 3 種類。ただしこの言語ではすべて「新しいローカルスコープを開き、本体を上から一度だけ読む」ことを意味する。`func` という単語でも、実言語の関数宣言や呼び出しの意味を持たせない。

- `let x_a 1739`: 現在のスコープに変数を束縛する。同一スコープ内で同名を再定義しない。
- 内側の同名束縛は外側を隠す。スコープを閉じれば、外側の束縛が再び見える。
- `nop p_qazwsxed`: 意味を持たないダミー文。環境もスタックも変更しない。
- `probe q_abcdefgh x_a`: この地点の観測マーカー。読み取り問題の末尾に一つだけ置く。
- プログラムの外側には暗黙の global scope がある。global の名前は active-stack の回答に含めない。

ブロックの種類は任意にネストできる。型検査、演算、条件分岐、ループ、関数呼び出しは導入しない。

### 3.2 五つの表記

| ID | 名前 | 開始行の例 | 終端行の例 |
|---|---|---|---|
| A | `braces` | `func n_qazwsxed {` | `}` |
| B | `generic_end` | `func n_qazwsxed` | `end` |
| C | `typed_end` | `func n_qazwsxed` | `end func` |
| D | `named_end` | `func n_qazwsxed` | `end func n_qazwsxed` |
| E | `padded_end` | `func n_qazwsxed` | `end junk z_aaaaaaaa` |

E の `junk z_aaaaaaaa` は全ブロックで同じ定数であり、対応するブロックの種類・名前・深さを一切表さない。この固定 suffix を文法として要求する。

D の種類キーワードは 4 文字、ブロック名は 10 文字なので、D と E の終端行は基本条件で同じ文字数になる。ただし**同じ文字数は同じトークン数ではない**。E を「完全な token-matched control」と呼ばない。

主比較は D−B。C−B で種類ラベル、D−C で名前ラベル、D−E で情報を含む冗長性の寄与を探索する。A は広く使われる区切りに近い補助 baseline とする。

### 3.3 字句・改行

一行一文とし、ブロックの開始・終端も一行を占める。文字コードは UTF-8、正規出力は LF。

```text
block-name := n_[a-z]{8}
variable   := x_[a-f]
nop-id     := p_[a-z]{8}
probe-id   := q_[a-z]{8}
value      := 1000 以上 9999 以下の十進整数
```

ブロック名はプログラム内で一意。深さ、出現順、構文カテゴリ、正解を符号化しない。`f1`、`f2`、`f3` のような連番は使わない。比較する表記間では完全に同じ名前を使う。

parser は行頭・行末の空白、LF / CRLF、空行を許容する。構造を決めるのは明示的な開始と終端だけであり、インデントに意味はない。主実験の renderer は全行を左寄せする。

文字列リテラル、コメント、任意の式、複数文を含む一行は未対応でよい。未対応入力を黙って受理しない。

### 3.4 二つの語彙条件

`natural`: `unit / func / area / let / nop / end` を使用する。

`nonce`: 予約語を episode 内で一貫した別の語に置換する。種類キーワードは互いに異なる 4 文字、`let / nop / end` は互いに異なる 3 文字にする。probe マーカーは共通の観測用表記として固定してよい。

nonce 語彙は事前に生成した 8 セットを固定し、セル内で可能な限り均等に割り当てる（セット間の件数差は最大 1）。pilot のようにセル内の family が 8 未満なら、一部セットの割り当てが 0 でもよい。同じ AST の A〜E に同じ語彙対応を使う。割り当ては seed だけで決まり、モデルの回答を見て変更しない。

`junk z_aaaaaaaa` は E 専用の固定 suffix として nonce 条件でもそのまま使う。自然語条件との違いとしてログに残す。

語彙対応と、各語が何を意味するかはプロンプトに明記する。これは「説明 + few-shot による適応」の実験であり、例示だけから意味を完全発見させる実験ではない。

## 4. タスク

### 4.1 T1: `scope_lookup` — 主タスク

入力はプログラムの prefix と観測マーカー。回答は、その地点で見える指定変数の値だけとする。A〜E すべてで同じ整数が正解になる。

説明用の D の例を示す。ここだけ読みやすさのためにインデントを付ける。

```text
let x_a 1048
unit n_qazwsxed
  let x_a 7321
  func n_plmoknij
    let x_a 5916
    area n_uhbygvtf
      let x_a 8604
      nop p_abcdefgh
    end area n_uhbygvtf
    probe q_zxcvbnma x_a
```

正解は `5916`。ブロック名を答えとしてコピーする問題ではない。

コードは意図的に途中までであり、EOF 時点に開いたスコープが残ってよい。モデルに未閉鎖部分の修復・補完を要求しない。**probe より後の終端やコードを入力に含めない。** 将来の閉じラベルが手掛かりになるのを防ぐ。

二つの観測位置を、同じ基礎プログラムから作る。

- `before_close`: 注目する閉鎖列の直前。これから閉じられる最深部の束縛がまだ有効。
- `after_close`: 閉鎖列の直後。閉じたスコープの束縛は無効になっている。

主実験は `after_close`。`before_close` は対照だが、入力のそれ以前に終端が含まれる場合もあるので、厳密な「効果ゼロの負の対照」とは呼ばない。

生成器は、before / after で正解が変わるようにする。最深部の束縛と閉鎖後の可視束縛に異なる値を置く。別の既に閉じた sibling に同じ変数の囮束縛を置く。

正解は必ず定義済みの値。P0 / P1 では未束縛変数を混ぜない。

### 4.2 T2: `active_stack` — 構造理解

T1 と同じ prefix を用い、probe 地点で**まだ開いている**ブロックの名前を、外側から内側への順序で JSON 配列にして答えさせる。

上の例の正解:

```json
["n_qazwsxed", "n_plmoknij"]
```

すでに閉じた `n_uhbygvtf` を入れてはいけない。global だけの場合は `[]`。

直前の終端に書いてある名前だけを答える `last_closed` 問題は、D で答えを直接読めるため主タスクにしない。

### 4.3 T3: `ast_to_source` — 生成

同じ AST を、中立的な親子関係テーブルで与え、指定された表記の完全なプログラムに変換させる。

```text
node  parent  order  tag    kind  name        variable  value  payload
r     -       0      ROOT   -     -           -         -      -
a7    r       0      LET    -     -           x_a       1048   -
b2    r       1      SCOPE  unit  n_qazwsxed  -         -      -
c9    b2      0      LET    -     -           x_a       7321   -
```

テーブルの行順は、parent が先に出る topological order とし、その中の sibling の提示順は固定 seed で変えてよい。実際のプログラムの順序は `order` フィールドが決める。node ID に深さや正解の列順を埋め込まない。

AST を JSON のネストや `{}` で見せ、A だけがコピーで済む入力にはしない。テーブルにも帰納的な構造の手掛かりがあることは認め、完全に無偏りな表現だとは言わない。

採点は、対象表記の parser で完全に parse でき、得られた AST が元の AST と一致するかで行う。内部 node ID とソース位置は比較から除く。構文カテゴリ、ブロック名、文順、束縛、値、nop payload は比較に含める。

T3 の body 長は P0 / P1 では filler 最大 32 文、深さ最大 8 に制限する。無意味な長い文字列のコピーで出力上限を消費する実験にしない。読み取り用の巨大 prefix と同じ難易度の実験ではないことを明記する。

D / E は正解の出力自体が長い。全条件に十分な共通上限を与え、正答率と実出力トークンの両方を報告する。「同じ正解トークン数」を無理に要求しない。

## 5. 問題生成と独立オラクル

### 5.1 基礎データ

`CaseFamily` は、表記・nonce 語彙・LLM の回答から独立して生成する。

保持するものは、基礎 AST、主対象変数、before / after の cut point、深さ、filler 数、閉鎖数、sibling 配置、各種 seed である。

cut point は AST の depth-first event 列における位置で表す。イベントは Open / Close / Let / Nop である。prefix を作るときだけ、選んだ cut point の末尾に一つの probe を追加する。

### 5.2 決定的生成

RNG のアルゴリズムを固定する。`java.util.Random(seed)` を使用し、変更時は generator version を上げる。

以下を別 seed にする。

```text
structureSeed
identifierSeed
valueSeed
fillerSeed
lexiconSeed
exampleSeed
scheduleSeed
bootstrapSeed
```

子 seed は `SHA-256(masterSeed || namespace || index)` の先頭 8 byte を big-endian signed Long として読む。可変長要素には長さ prefix を付ける。実装言語の `hashCode` を seed 導出に使わない。

### 5.3 生成ルール

- peak depth は global を除く最大同時 open 数。
- 注目する閉鎖数は `max(1, min(depth - 1, ceil(depth / 2)))`。主 grid の depth は 2 以上なので、少なくとも一つのブロックが after でも残る。
- 各基礎プログラムの注目経路には before / after で異なる束縛が見えるように配置する。
- ケースの半分では、after の最内スコープ自体には対象変数を置かず、さらに外側から lookup させる。depth=2 で成立しない配置は global からの lookup にする。
- 各ケースに閉じた sibling を 2 個置く。囮の対象変数値を含ませ、どちらも観測時点では無効とする。
- 構文カテゴリは seed で決め、grid 全体で偏りを監査する。all-same-kind は P2 の独立条件にする。
- 値は各プログラム内で重複しない 4 桁の整数。値の大きさ、出現順、名前から正解を予測できる規則を作らない。
- filler は最後の注目 Open と before cut の間に置く `nop` 文数。正答値は含めない。
- 完全なプログラムとして閉じるまで AST を生成するが、T1 / T2 のプロンプトには probe 以後を入れない。

`filler=0` は「追加 nop がゼロ」であって、開始と終端の間がゼロ文字・ゼロトークンであることを意味しない。

### 5.4 二系統のオラクル

**ReferenceOracle:** AST を再帰的に走査して、cut point の環境・open scopes を求める。renderer、source parser、event-based evaluator を呼ばない。

**ParsedPrefixOracle:** 実際に render した prefix を別実装の行 parser で読み、明示的なスタックで環境を計算する。

次を API 実行前に全問で確認する。

```text
ReferenceOracle(AST, cut)
  == ParsedPrefixOracle(renderPrefix(AST, cut, A))
  == ParsedPrefixOracle(renderPrefix(AST, cut, B))
  == ParsedPrefixOracle(renderPrefix(AST, cut, C))
  == ParsedPrefixOracle(renderPrefix(AST, cut, D))
  == ParsedPrefixOracle(renderPrefix(AST, cut, E))
```

さらに、全表記の `parseProgram(renderProgram(ast))` が、metadata を除いた同じ AST に戻ることを確認する。

### 5.5 Oracle / generator の監査

10,000 個の小さな random AST に対するオフライン property test を用意する。次を含める。

- 変数名を全箇所で一貫して変更しても、その変数の lookup 結果が変わらない。
- nop の挿入で答えが変わらない。
- 閉じた sibling の値だけを書き換えても、観測地点の答えは変わらない。
- 可視束縛だけを書き換えると、T1 の正解だけが対応して変わる。
- D の閉じ名の取り違え、C / D の閉じ種類の取り違え、E の固定 suffix の誤りを厳密に拒否する。
- global での close は underflow。prefix の EOF に残る open scope は正常。完全なプログラムの EOF に残る open scope は異常。

最後の二つを同じ parser モードで曖昧に扱わない。

## 6. Few-shot とプロンプト

### 6.1 固定方針

各 API リクエストは独立した 1 episode とする。過去の実験結果、別表記、別条件の正解、自己修正履歴を持ち込まない。

主設定は 8-shot、英語の簡潔な意味説明 + 文法説明 + 8 例 + 1 テスト問題。T1 / T2 / T3 はそれぞれ専用の例示バンクを持つ。

同じ family・同じ task について、A〜E は同一の例示 AST と正解を同じ順序で用いる。構文説明は必要な箇所だけ変更する。

例示は test とは別の seed namespace から生成する。深さ 2〜4、filler 0〜8。構造 hash と名前集合の重複を検査する。単なる名称変更で同じ構造を完全暗記できる test は作らない。

LLM に「冗長な文法のほうが優れているかを調べている」と教えない。条件名 A〜E、`named_end` などの研究上のラベルをプロンプトへ出さない。

### 6.2 T1 のテンプレート

```text
You will read a prefix of a small scope language.
Every block, regardless of its kind, creates a local scope.
Statements are processed once, from top to bottom.
A let statement binds a variable in the current scope.
The nearest still-open scope containing the variable determines its value.
Bindings in closed scopes are no longer visible.
A nop statement has no effect.
The final probe marks the observation point.
Open blocks at the end of this prefix are intentional.
Do not complete or repair the program.
Return only the decimal integer visible at the final probe.

SYNTAX
<description generated from this condition's grammar and lexicon>

EXAMPLES
Example 1
Program prefix:
<example source>
Answer:
<oracle value>

... exactly K examples, with no omitted examples in real requests ...

TEST
Program prefix:
<test source ending at its probe>
Answer:
```

上の `<...>` はテンプレートの差し込み位置であり、実プロンプトに未展開で送ってはいけない。`... exactly K examples ...` も説明用表記で、実際には例をすべて生成する。

T2 は出力要求だけを active-stack の JSON 配列に替える。T3 は中立テーブルからの完全な出力を要求する。

明示的な chain-of-thought、理由説明、自己評価スコアは要求しない。ただしモデルの推論機能は設定した effort で利用してよい。

### 6.3 出力形式を制約しすぎない

主実験は通常の text 出力を使う。Structured Outputs、grammar-constrained decoding、tool call、外部コード実行は使用しない。構文生成の誤りを外部の制約機構が消してしまうのを避ける。

T2 の JSON 配列はプロンプト上の回答形式であり、API の constrained JSON mode ではない。

### 6.4 Token と距離の記録

操作する独立変数はまず `fillerStatements` と `peakDepth` である。測定せずに「opener / closer が 5,000 tokens 離れている」とは書かない。

以下を記録する。

- ソースの行数、文字数、UTF-8 byte 数。
- 注目 opener、関連する束縛、close 列、probe の行・文字・byte offset。
- episode 全体の事前 token count と API が返した実 input / output / reasoning / cached tokens。
- 例示、文法説明、テスト source のそれぞれの文字数・byte 数。

公式 token-count endpoint が返すのはリクエストの入力トークン数であり、任意 source span の厳密な tokenizer offset ではない。prefix の token count の差分を使う場合も、境界依存がある近似として区別する。

## 7. 実験プリセットと設定凍結

### 7.1 モデル設定

初期設定は以下とする。Terra の公式資料には `none / low / medium / high / xhigh / max` の reasoning effort が記載されているが、実アカウントで smoke を通す。[1]

```json
{
  "model": "gpt-5.6-terra",
  "reasoningEffort": "low",
  "maxOutputTokensReading": 8192,
  "maxOutputTokensGeneration": 16384,
  "temperature": null,
  "topP": null,
  "fewShotCount": 8,
  "indentation": "none",
  "replicates": 1
}
```

`null` は JSON request に null を送る意味ではなく、API パラメータを送らないという内部設定である。対応を確認していない temperature、top_p、seed、stop を送らない。

`low` が使えなければ自動で別設定に変えず、preflight で停止する。修正後はモデル設定・protocol hash を更新し、全表記に同じ設定を適用する。

8192 / 16384 は上限の初期値である。pilot で出力打ち切りが発生するなら、同一タスクの全表記について上限を見直し、主実験の前に固定する。長い D だけ上限を増やさない。

同じリクエストを複数回送っても完全に同じ答えになるとは仮定しない。replicate は model-side seed に依存せず別 trial として扱い、独立ケース数には加えない。

### 7.2 Smoke

手作りで正解を監査した 4 ケースを用意する。outer lookup、shadowing、複数 close、閉じた sibling の囮を含める。

| 実行 | 計画する生成リクエスト数 |
|---|---:|
| P0: T1 × 4 ケース × 5 表記 | 20 |
| P1: T1 / T2 / T3 × 各 4 ケース × 5 表記 | 60 |

smoke は自然語彙のみ。モデル性能の結論を出すための標本ではない。

### 7.3 Pilot

読み取りの基礎 family は次で作る。

```text
depth            = [2, 4, 8]
fillerStatements = [0, 32, 128]
seedsPerCell     = 4
family count     = 3 × 3 × 4 = 36
lexical regimes  = natural + nonce = 2
views            = before_close + after_close = 2
syntaxes         = A, B, C, D, E = 5
```

| タスク | 計算 | 生成リクエスト数 |
|---|---|---:|
| T1 scope lookup | 36 × 2 語彙 × 2 views × 5 表記 | 720 |
| T2 active stack | 36 × 2 語彙 × 2 views × 5 表記 | 720 |
| T3 AST-to-source | 別の短い 36 families × 2 語彙 × 5 表記 | 360 |
| 合計 | | 1,800 |

T3 の 36 families は depth `[2,4,8]`、filler `[0,8,32]`、各セル 4 seed で生成する。

P0 は T1 の 720 件だけを実装・実行できればよい。P1 で残りを追加する。pilot の目的は、天井 / 床効果、打ち切り、プロンプトの曖昧さ、API 利用量、難易度を確認することである。

pilot を見て主実験の難易度・サンプル数を変えてよい。ただし pilot の結果を確認的な主実験の標本に混ぜない。D に有利だったケースだけを選んで主実験へ移さない。

### 7.3a 強いモデルで全問正解に近くなった場合

Terra で最初の grid が簡単すぎる可能性は残る。全条件の正答率が概ね 98% 以上、または有効な不一致ペアがほとんどない場合は、そのまま数だけ増やす前に pilot の難易度を上げる。

次の候補は depth `[8,16,32]`、filler `[128,512,2048]`。新しい seed namespace を使い、全表記に同じ拡張を行う。depth 32 に対応する内部 JSON / AST の入れ子が parser の資源上限に収まることも監査する。

難易度調整では条件名を隠して合算した正答率と、全条件に共通するエラー分布を見る。D が勝つまで設定を探すのではない。主実験を凍結する前の探索であることと、試した grid をすべて記録する。

それでも読み取りが完全なら、それ自体を結果として残す。別 effort や生成タスクで差が出ても、読み取りで差を検出できたことにはしない。

### 7.4 初期の主実験案

```text
task             = scope_lookup
view             = after_close
depth            = [2, 4, 8, 16]
fillerStatements = [0, 32, 128, 512]
seedsPerCell     = 32
family count     = 4 × 4 × 32 = 512
lexical regimes  = natural + nonce = 2
syntaxes         = A, B, C, D, E = 5
requests         = 512 × 2 × 5 = 5,120
primary contrast = named_end - generic_end
```

これは初期 grid であって、検出力計算によって最適性が確認された件数ではない。pilot 後に精度・必要な差の大きさ・利用量を踏まえて改訂し、その後に凍結する。

before_close も同じ families で主実験に含める場合は、合計 10,240 件になる。含めるかどうかを主実験の前に決める。after の結果を見て、有利なときだけ before を追加して交互作用を「確認的結果」にしない。

T2 / T3 の主実験は別 suite とし、P0 の主実験に必須としない。

### 7.5 実行順

同じ family・語彙・task・view の A〜E を一つの matched block とし、その内部順序をランダム化する。block 自体の順序も固定 seed でランダム化する。

D を全部実行してから B を全部実行する方式は使わない。時刻、混雑、モデル更新、キャッシュの偏りを抑えるためである。

同じ block の試行は近い時間帯に実行するが、別リクエストとして独立したプロンプトを送る。複数条件を一つの会話に入れない。

API 上限により分割する場合は、できる限り matched block の境界で止める。途中で止まった block は resume で残りを実行し、欠測状況を記録する。

### 7.6 Freeze

主実験の前に `freeze` コマンドで次を保存する。

```text
protocol.json
preregistration.md
dataset-manifest.json
prompt-template-hashes.json
capabilities.json
git commit + dirty-tree status / diff hash
Scala / JDK / sbt versions
requested model + API endpoint + run date
```

主実験には、保存された protocol hash を明示して実行させる。生成済み test prompt、採点規則、対比、停止条件を主実験の途中で変更したら、新しい run として扱う。

snapshot ID が公式に提供され、かつ利用できる場合は固定を優先する。存在を確認していない日付付きモデル ID を作らない。alias しか使えない場合は requested / returned model、日時、response ID を保存し、backend の完全固定を保証できないことを報告する。

## 8. 自作 LLM call library

### 8.1 モジュール境界

```text
llm-core
  json/          JSON 値・parser・serializer
  http/          JDK HttpClient transport
  model/         Request / Response / Usage / Error
  openai/        Responses API adapter / token counter
  retry/         純粋な retry policy と待機時間計算

bench
  lang/          AST / rendering / parsing / oracle
  data/          deterministic generators / manifests
  prompt/        ICL templates
  run/           limits / scheduling / retries / persistence
  score/         deterministic graders
  stats/         paired analysis / bootstrap
  report/        Markdown / CSV
  cli/           commands
```

`llm-core` は `bench` に依存しない。学習、構文、実験条件を知らない、小さな再利用可能ライブラリとする。

`LlmClient.generate` は**ちょうど一回の HTTP 試行**を表す。retry を内部に隠さない。runner が retry policy に従って再試行し、毎回の上限確認とログ記録を行う。二重 retry loop を作らない。

### 8.2 インターフェース

次の型を基準に実装する。細かな private helper は追加してよい。

```scala
package locality.llm

final case class TextMessage(role: String, content: String)

final case class LlmRequest(
  model: String,
  instructions: String,
  input: Vector[TextMessage],
  reasoningEffort: Option[String],
  maxOutputTokens: Int
)

final case class TokenUsage(
  inputTokens: Long,
  outputTokens: Long,
  totalTokens: Long,
  cachedInputTokens: Option[Long],
  cacheWriteTokens: Option[Long],
  reasoningTokens: Option[Long]
)

final case class LlmResponse(
  responseId: String,
  returnedModel: String,
  status: String,
  text: String,
  refusals: Vector[String],
  incompleteReason: Option[String],
  usage: Option[TokenUsage],
  requestId: Option[String],
  rawBody: String
)

sealed trait LlmError
final case class ApiError(
  httpStatus: Int,
  code: Option[String],
  message: String,
  retryAfter: Option[String],
  requestId: Option[String],
  rawBody: String
) extends LlmError
final case class TransportError(
  message: String,
  ambiguousOutcome: Boolean
) extends LlmError
final case class DecodeError(
  message: String,
  rawBody: String
) extends LlmError

trait LlmClient:
  def generate(request: LlmRequest): Either[LlmError, LlmResponse]
  def countInputTokens(request: LlmRequest): Either[LlmError, Long]
```

`role` は内部検証で `user / assistant / developer / system` のみ許容する。実験では `instructions` と一つの user episode を基本にする。

この同期インターフェースを bounded worker pool から呼ぶ。未使用の Future / effect framework / streaming abstraction を先回りして作らない。

### 8.3 HTTP と秘密情報

JDK の `HttpClient` を一つ再利用する。[6]

```text
base URL            = https://api.openai.com/v1
connect timeout     = 15 秒
request timeout     = 180 秒
initial concurrency = 4
maximum body size   = 16 MiB
```

base URL は設定可能にして、localhost の mock server でテストする。API キー付きの実行では HTTPS を必須とし、loopback のテスト時だけ HTTP を許可する。redirect は追わず、認証情報を別 host へ転送しない。

認証は環境変数 `OPENAI_API_KEY`。任意の `OPENAI_PROJECT` / `OPENAI_ORGANIZATION` は、それぞれ `OpenAI-Project` / `OpenAI-Organization` header に渡す。[5] 値が存在しないときは header 自体を付けない。

キーを CLI 引数に入れない。ログには認証 header を保存しない。例外、レスポンスエラー、診断表示にも既知の秘密値の redaction を適用する。

body size 上限は body の読み込み中に適用する。ストリームを利用する場合は成功・失敗のどちらでも必ず閉じる。

### 8.4 Responses API

実験が送るリクエストの形は以下。文字列は必ず JSON serializer でエスケープする。[2]

```json
{
  "model": "gpt-5.6-terra",
  "instructions": "Experiment task instructions, rendered here.",
  "input": [
    {
      "role": "user",
      "content": "The complete independent few-shot episode, rendered here."
    }
  ],
  "reasoning": {
    "effort": "low"
  },
  "max_output_tokens": 8192,
  "store": false,
  "stream": false,
  "truncation": "disabled"
}
```

endpoint は `POST /v1/responses`。tools、previous_response_id、conversation、reasoning summary、background mode は使用しない。

`max_output_tokens` は可視回答だけでなく reasoning tokens も含む上限である。[3] 「答えは整数だから上限 16 tokens でよい」としない。

レスポンスの扱い:

- `output` 配列を走査し、assistant の `message` 内の `output_text` を抽出する。先頭 item が message だと仮定しない。
- phase が提供されて final / 非 final を識別できる場合は final message の text を採点対象にする。phase がない通常応答では該当 message の text part を順に結合する。
- part 境界や選択した message の情報もログに保持する。勝手に prose を削ったり、コードを探して抜き出したりしない。
- reasoning item や summary を回答に連結しない。SDK の convenience property の存在を raw HTTP 応答に対して仮定しない。
- `status`、`incomplete_details`、refusal、usage 欠損を明示的に扱う。incomplete でも得られた本文と usage は捨てない。
- `input_tokens_details.cached_tokens` と、存在する場合の `cache_write_tokens`、`output_tokens_details.reasoning_tokens` を保存する。欠損は 0 ではなく unknown。
- unknown な追加フィールドは許容し raw body に残す。必要フィールドの型違いは DecodeError とする。

`store:false` は Responses の保存設定として使う。組織・project のデータ共有設定の代わりと考えず、無償枠の適用は利用者側の設定と利用画面で確認する。[2][4]

### 8.5 Token-count endpoint

`POST /v1/responses/input_tokens` を同じ内部 HTTP / JSON 部品で呼ぶ。[7]

送信するのは生成時と同一の model / instructions / input。生成専用パラメータを無条件に転送せず、この endpoint が受け付ける項目だけを渡す。

```json
{
  "model": "gpt-5.6-terra",
  "instructions": "Experiment task instructions, rendered here.",
  "input": [
    {
      "role": "user",
      "content": "The complete independent few-shot episode, rendered here."
    }
  ]
}
```

返却される `input_tokens` を保存し、プロンプト hash ごとにローカルにキャッシュする。生成リクエスト数と token-count 用 HTTP リクエスト数は別に表示する。

token-count が利用できない場合、生成器やオフライン集計はそのまま使える。live runner の厳密な事前予約モードは停止する。利用者が明示的に soft-budget モードを指定した場合だけ概算で進め、厳密な token cap と表示しない。

### 8.6 Retry

上限は一論理試行につき最大 4 HTTP attempts、retry を含む総経過時間 600 秒とする。

retry 対象は rate-limit 型の 429 と一時的な 5xx。認証・権限・不正パラメータ・モデル不存在・課金 / quota の是正が必要なエラーは再試行しない。[8]

有効な `Retry-After` があれば秒数 / HTTP-date として解釈する。待機を尊重すると総時間上限を超える場合は、その試行を停止する。server 指示を無視して上限時間より短く待たない。

header がないか不正なときは、`uniform(0, min(60秒, 1秒 × 2^(attempt-1)))` の full jitter とする。retry 用 RNG は問題生成用 RNG から分離する。

通信が途中で切れた場合は、サーバー側で処理されたか不明である。既定では `ambiguous_outcome` として止め、`--retry-ambiguous` を利用者が明示した場合のみ再試行する。重複課金やサーバー側二重実行を完全に防げるとは言わない。

completed な誤答、形式違反、refusal、max_output_tokens による打ち切りを retry しない。返答を見てから条件や予算を変える self-repair も主実験には入れない。

### 8.7 自作 JSON

JSON ADT は null / boolean / number / string / array / object を実装する。number は BigDecimal とし、token count を Long に変換するときは厳密に範囲・整数性を検査する。

次を必須テストとする。

```text
引用符、backslash、改行、tab、制御文字の escape
日本語と supplementary Unicode / surrogate pair の round trip
空配列、空 object、入れ子、true / false / null
負数、小数、指数表記、Long を超える整数の保持
trailing comma、途中 EOF、不正 escape、余剰 input の拒否
不正な unpaired surrogate の拒否
重複 object key の拒否
最大深さ 128、number の字句長上限 1024 の検査
```

serializer は通常出力と canonical 出力を持つ。canonical は object key を辞書順にして hash の安定性を確保する。raw HTTP body は別途保存し、JSON の再生成物を raw と呼ばない。

## 9. Runner・利用上限・再開

### 9.1 利用枠を使う前提

2026-10-05 に確認した公式ヘルプでは `gpt-5.6-terra` は大きい方の共有トークン枠に記載されている。Tier 3〜5 は同グループ合計 10M tokens / 日、Tier 1〜2 は 2.5M tokens / 日。対象 project の共有設定、他用途との合算、上限を越える request の扱いに注意が必要である。更新境界は 00:00 UTC。[4]

利用者の Tier、当日の残枠、他アプリの使用量を推測しない。「湯水のように使える」という利用前提は実験規模を大きく取る理由として受け止めるが、コード内で `unlimited=true` や課金額ゼロを仮定しない。

通常の Responses 呼び出し + ローカル採点で進める。OpenAI の Evals / fine-tuning / tool-use サービスの無料条件と取り違えない。[4]

### 9.2 上限

live 実行では、次の三つを必須にする。preflight だけは生成が 1 論理 trial と固定されているため、`maxGenerationCalls=1` を内部設定し、残り二つを CLI で要求する。

```text
maxGenerationCalls  論理生成 trial を新しく開始できる数
maxHttpAttempts      count / generate / retry を含む実 HTTP 試行の上限
localTokenCap        この runner が管理する生成 token の利用上限
```

`--execute` がない `run` は計画表示だけにし、HTTP 通信を行わない。token count 自体も HTTP なので `plan --count-tokens` には `--execute` と HTTP 上限を要求する。

厳密な事前予約モードでは、生成前に次を予約する。

```text
reserved = countedInputTokens + maxOutputTokens + 256
```

256 は token-count と本番の差異に備えたローカル余裕であって、provider の将来仕様に対する保証ではない。

```text
knownUsage + unresolvedReservations + inFlightReservations + newReservation
  <= localTokenCap
```

を満たさなければ、新しい生成を開始しない。完了時に usage が得られれば実値へ置き換える。usage 不明の失敗は 0 とせず未解決予約として残す。実 input count が事前 count を想定以上に上回ったら停止して診断する。

この cap はこの runner のローカル予算であり、**組織全体の当日無料残枠を保証しない**。token count の HTTP 処理と生成 usage の token 合計も混同しない。

rate limit は concurrency に加え、設定した max RPM / TPM に従って throttle する。初期値は 60 RPM / 200,000 TPM。利用枠と処理速度の上限は別物として扱い、全 HTTP attempts を RPM 管理へ通す。

### 9.3 永続化

```text
runs/<run-id>/
  protocol.json
  manifest.json
  capabilities.json
  families.jsonl
  cases.jsonl
  gold.jsonl
  schedule.jsonl
  usage-ledger.jsonl
  events.jsonl
  trials/<trial-id>/
    request.json
    token-count.json
    attempt-001-meta.json
    attempt-001-body.txt
    attempt-002-meta.json
    attempt-002-body.txt
    terminal.json
  scores.jsonl
  summary.csv
  paired-comparisons.csv
  errors.csv
  report.md
```

`gold.jsonl` と prompt は構造的に分離する。Expected / answer / stack などの正解 metadata を JSON serialization の都合で request に混入させない。

request / response body、出力 text、タイムスタンプ、status、HTTP request ID、reasoning 設定、usage、retry 理由、採点結果を残す。秘密値のみをマスクし、その事実を記す。

`trial-id` は protocol hash、family ID、task、view、表記、語彙セット、replicate index、request hash を含めた canonical hash で作る。API キーは hash 対象にも入れない。別 replicate を prompt が同じだからといって統合しない。

### 9.4 Resume と破損回復

- 一つの run directory は file lock で単一 writer にする。
- trial の terminal record は一時ファイル + rename で確定する。body / metadata を先に保存してから terminal に参照させる。
- JSONL は単一 writer から追記し、再起動時に末尾の不完全レコードを隔離する。途中の破損は黙って読み飛ばさず停止する。
- terminal がある trial は再送しない。正解だけでなく誤答・refusal・打ち切りも終端状態である。
- dispatch 記録があるが terminal がない trial は「未送信」と決めつけない。ambiguous として予約を保持し、利用者の方針に従う。
- protocol / dataset / prompt hash が変わった resume は拒否し、新規 run を要求する。
- Ctrl-C では新規 dispatch を止め、終了できた応答を保存する。完了確認できなかった要求は ambiguous と記録する。

ローカルの結果再利用は resume のためであり、model replicate の代用品ではない。集計の再実行では API を呼ばない。

### 9.5 時間とキャッシュ

保存する時間は queue wait、token-count、各 HTTP attempt、backoff、logical trial 全体を分離する。

API latency はネットワーク、混雑、キャッシュ、出力長にも依存する。副指標として報告し、内部計算量の証拠として単独で使わない。retry のある試行とない試行を区別する。

prompt caching の token は使用量内訳として記録する。cache hit を「モデルが前の正解を学習した」と解釈しない。トークン計測、コスト見積もり、課金画面の確認を別の概念として扱う。

## 10. 採点と統計

### 10.1 回答の採点

T1 は前後の whitespace を trim し、回答全体が一つの十進整数であることを要求する。文章の途中から正しい数値を探し出して正解にしない。

T2 は回答全体を JSON として parse し、文字列だけの配列として名前と順序の完全一致を調べる。

T3 は完全なプログラムとして strict parse し、AST の完全一致を調べる。parser が最初に拒否した位置と error code を保存する。最初の parse error の件数を、ソース全体の全エラー数と呼ばない。

主採点は code fence や補足説明を許容しない。補助指標として「回答全体を覆う一組の code fence だけを除去した採点」を出してよいが、主指標と混ぜない。回答に応じて都合よく normalization を増やさない。

### 10.2 結果カテゴリ

```text
correct
wrong_answer
invalid_answer_format
invalid_generated_syntax
valid_syntax_wrong_ast
refusal
incomplete_output
api_rejected
transport_failure
ambiguous_outcome
decode_failure
not_dispatched
```

前 7 種はモデル側の評価可能な結果で、correct=1、他=0 とする。後半のインフラ・未実行状態は missing として区別する。

`incomplete_output` の本文に偶然正解があっても、主指標では成功としない。通常の completed 回答と同じ運用結果ではないためである。本文だけの補助採点は保存してよい。

### 10.3 分母

各集計に次を必ず出す。

```text
planned / dispatched / terminal / model-evaluable / correct
infrastructure missing / not-dispatched / incomplete / refusal
matched families available / matched families planned
```

予算によって未実行の試行を、モデルが間違えたと扱わない。未完了 run は provisional と表示する。

主たる対応比較は、比較する両条件が model-evaluable なケースに限る。natural / nonce を合算する主比較では、family 内の両語彙について B と D が揃った family を使う。A / C / E の障害だけで B−D の有効ペアまで捨てない。

別に `correct / dispatched` の保守的な運用成功率も出す。応答未確定がある場合は、未確定が全部成功した場合の上側も併記する。

主実験でインフラ欠測が全体の 1% を超えた場合は、確定的な優劣の結論を出す前に欠測の原因と条件偏りを調査する。missing を D に最悪 / 最良に割り当てた感度分析も出す。

### 10.4 主効果量

primary は `scope_lookup / after_close / D−B` の正答率差。natural と nonce を同じ重みで平均し、depth × filler の各セルも同じ重みで平均する。

family i の差を次のように計算する。

```text
d_i = mean_over_lexical_regimes(
        correctness(i, named_end) - correctness(i, generic_end)
      )
```

replicate を増やした場合は、同じ family / 語彙 / 表記内で先に平均する。

差は percentage points で示す。相対エラー削減率も補助的に計算してよいが、baseline error=0 のときは未定義として `NA` にする。

D−E、D−C、C−B、A との比較、語彙別・深さ別・距離別結果は、事前に個別の検定計画を固定しない限り探索的と明記する。

### 10.5 Cluster bootstrap

Scala で 10,000 回の stratified paired cluster bootstrap を実装する。

1. stratum は depth × filler。
2. 各 stratum 内の family ID を、その stratum の有効 family 数だけ復元抽出する。
3. 選ばれた family の全語彙・表記・replicate を一緒に持ち運ぶ。
4. family 内の差、stratum 内の平均差、strata の等重み平均差を順に計算する。
5. bootstrap 分布の 2.5 / 97.5 percentile を報告する。

percentile はソート済み配列に対する位置 `(n-1)*p` の線形補間に固定する。bootstrap seed を保存する。

同じ AST の 5 表記、2 語彙、複数 replicate を独立な n として扱わない。基本単位は structural family。8 個の固定 nonce 語彙への一般化と、あらゆる人工語彙への一般化も区別する。

すべての差が 0 など bootstrap 分布が退化した場合、`[0,0]` を「真の効果が厳密にゼロと判明した」という区間として出さない。退化フラグを立て、観測差、discordant family 数、天井 / 床効果を報告する。必要なら新しい holdout を使う難易度拡張を次の実験として提案する。

途中結果を監視して、D の CI が正になった瞬間に止める運用は禁止。停止条件は事前の件数、時間 / 予算上限、障害条件とする。

### 10.6 共通指標

T1: strict answer accuracy、invalid format、wrong value。

T2: exact stack accuracy、答えの要素数差、未知名・閉鎖済み名・順序違いの診断。

T3: strict syntax validity、exact AST accuracy、最初の close-kind / close-name mismatch、EOF / underflow / extra-source error。

全タスク: input tokens、output tokens、reasoning tokens、cached tokens、打ち切り率、retry 率、遅延、tokens per correct result。正答ゼロの tokens per correct は 0 ではなく `NA`。

`outputTokens - reasoningTokens` は、内訳が揃った場合のみ non-reasoning output の推定として表示する。回答文字列だけの厳密な token count と同一視しない。

### 10.7 レポートの解釈

結論は観測に合わせる。

- T1 / T2 改善、T3 悪化: 読み取りの手掛かりになる一方、ラベルを生成する負担がある。
- D と E が同程度で B より良い: ラベル情報ではなく、終端の長さ・形・区切り方の効果かもしれない。
- D が C より良い: この課題では種類だけでなく名前の反復に追加効果がある可能性がある。
- 差がない: このモデル・prompt・難易度・標本では差を検出できなかった。直ちに一般的な同等性を証明したとは言わない。
- D が悪い: 冗長性のコスト、対応づけの追加負担、tokenization、プロンプト長などを調べる。失敗例を除去して結果を作り直さない。

正答率が改善した場合も「依存距離が短くなったから」と単一原因に断定しない。繰り返された名前による検索手掛かり、区切りの識別、注意の向けやすさなど、複数の説明と整合し得る。

## 11. レポートの必須構成

`report.md` は外部 LLM を呼ばず、保存されたデータだけから生成する。

```text
1. 実行状態と要約
2. 実験条件、protocol hash、モデル、日時
3. 計画件数 / 実行件数 / 有効 family 数 / 欠測
4. 主比較 D−B と対応付き効果量
5. 5 表記の正答率・打ち切り・使用量
6. natural / nonce、depth、filler の内訳
7. D−E / D−C / C−B 等の探索的結果
8. 読み取りと生成の比較（実施したタスクのみ）
9. 代表的失敗例
10. 制約と次の実験候補
```

失敗例は事前規則で選ぶ。D 正解 / B 誤答、D 誤答 / B 正解、両方誤答から、各カテゴリの trial hash が小さい順に最大 3 件を載せる。片側に都合のよい例だけを並べない。

各例には両表記の入力、raw answer、正解、エラー分類、trial ID を含める。非常に長い入力はファイル参照を出し、抜粋には切り出した行範囲を明記する。

表の CSV も出す。P0 / P1 に plotting library は不要。グラフが必要になれば、保存済み数値から静的 SVG を Scala で生成する段階を追加する。

## 12. P2 の実験候補と注意

### 12.1 推論予算

同じケースを `none / low / medium` で比較する。主 run の途中で effort を変えない。効果が低 effort でのみ見える場合も重要な結果だが、「モデル全般に読みやすい」と一般化しない。

### 12.2 インデントと例示数

インデントなし / 2 spaces を全条件で揃えて比較する。字下げそのものが構造情報を持つので、主比較と別因子にする。

few-shot 数 `[0,2,8,16]` を比較する場合、0-shot は意味・文法説明のみの条件とする。例示数と prompt 長が同時に変わることを記録する。

### 12.3 同一種類のネスト

全ブロックを `func` にする。C の型ラベルでは個々のブロックを区別できず、D との差が名前情報に関係するかを調べやすくなる。ただしこれだけを選んで一般的優越を主張しない。

### 12.4 Token-matched 条件

主比較は同じ AST を自然に表記したときの総合的な性能であり、入力 token 数を揃えるために内容を変えない。

補助実験で token 数を揃える場合、公式 counter で実数を確認し、padding の位置と意味を固定する。単に prompt の末尾に文字列を足すと probe の相対位置まで変わる。これを「構文だけの純粋な因果効果」と呼ばない。

prompt 長は表記という介入の結果でもある。回帰で長さを調整するだけで、その介入の直接効果が識別できたと考えない。

### 12.5 誤り修復

同じ AST に対応する event-level corruption を各表記へ適用する。例えば close 行を一つ削除する。D にしか存在しない名前誤記は、D 専用の追加診断にする。

壊れたソースから元 AST を一意に復元できないことがある。「生成時に知っていた元の答えがある」だけでは一意復元可能性の証明にならない。修復タスクには目標 AST、許容修復集合、または曖昧さを認める採点規則を与える。

### 12.6 局所窓

prefix の末尾だけを与える実験では、情報が欠けて正解を原理的に決められない問題が発生する。その場合に、元の AST を知る evaluator だけを根拠として誤答判定しない。

切り出した観測に整合する複数の completion が異なる答えを持つ場合は `UNKNOWN` を許容するなど、識別可能性のオラクルを先に設計する。P0 / P1 には含めない。

---

## 13. 実装タスク

ここから実装する。各タスクは、失敗するテスト、最小実装、テスト実行、局所的な差分確認の順で進める。既存リポジトリへ入れる場合、無関係なファイルやユーザーの変更を上書きしない。

新規プロジェクト名は `syntax-locality-icl`。外部サービスへの repository 作成や push は、この文書だけを根拠に実行しない。

### Task 1 — sbt 構成、依存ゼロの JSON、テスト runner【P0】

**Files:**

```text
build.sbt
project/build.properties
llm-core/src/main/scala/locality/json/Json.scala
llm-core/src/main/scala/locality/json/JsonParser.scala
llm-core/src/main/scala/locality/json/JsonWriter.scala
llm-core/src/test/scala/locality/llm/CoreTests.scala
bench/src/test/scala/locality/bench/BenchTests.scala
.gitignore
.env.example
```

**Interfaces:** `Json.parse(String): Either[JsonError, Json]`、`Json.render(Json): String`、`Json.canonical(Json): String`。JsonError は offset と理由を持つ。

- [ ] `llmCore` と `bench` の sbt subproject を作り、bench が llmCore に依存する構成にする。Scala / JDK / sbt の指定を固定する。
- [ ] 外部 test framework を使わない小さな test runner を作る。named test、assertion、失敗時の非ゼロ終了、実行件数ゼロの拒否を実装する。bench 側には JDK 21 環境と UTF-8 ファイル入出力の bootstrap test を作り、最初から実行件数ゼロにしない。
- [ ] `Test / test` を各モジュールの test runner に接続する。`sbt test` で実際に両方の tests が走ることを確認する。
- [ ] 第 8.7 節の JSON テストを先に作り、失敗を確認してから実装する。
- [ ] `sbt "llmCore / Test / runMain locality.llm.CoreTests --suite json"` を通す。`sbt "show llmCore / libraryDependencies" "show bench / libraryDependencies"` で第三者ライブラリがないことを確認する。

`.gitignore` に `.env`、`runs/`、`target/`、IDE metadata を含める。`.env.example` に実キーを入れず、環境変数の名前と用途だけを書く。独自の `.env` loader は必須にせず、shell で export する方式を使う。

### Task 2 — HTTP transport と Responses adapter【P0】

**Files:**

```text
llm-core/src/main/scala/locality/llm/Model.scala
llm-core/src/main/scala/locality/llm/HttpTransport.scala
llm-core/src/main/scala/locality/llm/OpenAiResponsesClient.scala
llm-core/src/main/scala/locality/llm/RetryPolicy.scala
llm-core/src/test/scala/locality/llm/HttpTests.scala
llm-core/src/test/scala/locality/llm/ResponseTests.scala
```

**Interfaces:** 第 8.2 節の型と `LlmClient` を公開する。RetryPolicy は、error・attempt 番号・現在時刻から retry / stop と待機時間を返す純粋な関数にする。

- [ ] JDK の `com.sun.net.httpserver.HttpServer` を使った loopback mock を作る。テストで実ネットワーク先を参照しない。
- [ ] reasoning item の後に message が来る応答、複数 text parts、refusal、incomplete、usage 欠損、unknown fields、invalid JSON の fixture を先に作る。
- [ ] API body の JSON escaping、endpoint path、認証 header の付与とログからの除去をテストし、adapter を実装する。
- [ ] token-count endpoint と生成 endpoint が、それぞれ正しい request schema を使うことをテストする。
- [ ] 429 の rate limit / quota の区別、Retry-After の秒数 / date / 不正値、5xx、401 / 403 / 400 をテストする。
- [ ] timeout、巨大 body、redirect、秘密値を含む例外の redaction をテストする。
- [ ] `sbt "llmCore / Test / runMain locality.llm.CoreTests --suite http"` と `--suite responses` を通す。

時間依存テストには injectable Clock / Sleeper を使い、実際に何十秒も待たせない。これらは内部の小さな trait としてよい。

### Task 3 — AST と独立した参照オラクル【P0】

**Files:**

```text
bench/src/main/scala/locality/bench/lang/Ast.scala
bench/src/main/scala/locality/bench/lang/ReferenceOracle.scala
bench/src/main/scala/locality/bench/lang/EventOracle.scala
bench/src/test/scala/locality/bench/OracleTests.scala
```

**Interfaces:**

```scala
enum Kind:
  case UnitScope, FuncScope, AreaScope

enum Stmt:
  case Let(variable: String, value: Int)
  case Nop(payload: String)
  case Scope(kind: Kind, name: String, body: Vector[Stmt])

final case class Program(body: Vector[Stmt])
final case class CutPoint(eventCount: Int, probeId: String, variable: String)
final case class Observation(value: Int, activeScopes: Vector[String])
```

内部型として ParsedEvent、ParsedPrefix、OracleError を定義する。ReferenceOracle の入力は Program / CutPoint、EventOracle の入力は ParsedPrefix。どちらも `Either[OracleError, Observation]` を返す。

- [ ] 第 4.1 節の例の観測が value=5916、stack=unit と func の名前になる手作りテストを作る。
- [ ] shadowing、close 後の復帰、sibling、nop、global、範囲外 cut をテストする。
- [ ] 再帰的な ReferenceOracle と、明示スタックの EventOracle を別実装する。
- [ ] `sbt "bench / Test / runMain locality.bench.BenchTests --suite oracle"` を通す。

### Task 4 — 五つの renderer / parser【P0】

**Files:**

```text
bench/src/main/scala/locality/bench/lang/Syntax.scala
bench/src/main/scala/locality/bench/lang/Lexicon.scala
bench/src/main/scala/locality/bench/lang/Renderer.scala
bench/src/main/scala/locality/bench/lang/Parser.scala
bench/src/test/scala/locality/bench/SyntaxTests.scala
```

**Interfaces:** RenderSpec は SyntaxStyle / Lexicon / indentation を持つ。`renderProgram`、`renderPrefix`、`parseProgram`、`parsePrefix` を用意する。parse の失敗は code / line / column / message を持つ ParseError で返す。

- [ ] 手作り AST を 5 表記へ render した golden test を作る。
- [ ] D の名前不一致、C / D の種類不一致、E の suffix 不一致、underflow、完全 program の未閉鎖を拒否するテストを作る。
- [ ] prefix の未閉鎖は正常で、probe より後に文があれば異常となるテストを作る。
- [ ] LF / CRLF、空行、許可した whitespace の差をテストする。
- [ ] renderer / parser を実装し、全形式の round trip を通す。
- [ ] `sbt "bench / Test / runMain locality.bench.BenchTests --suite syntax"` を通す。

### Task 5 — ケース生成・対応付け・監査【P0】

**Files:**

```text
bench/src/main/scala/locality/bench/data/Seeds.scala
bench/src/main/scala/locality/bench/data/CaseFamily.scala
bench/src/main/scala/locality/bench/data/Generator.scala
bench/src/main/scala/locality/bench/data/DatasetAudit.scala
bench/src/test/scala/locality/bench/GeneratorTests.scala
```

**Interfaces:** `generateFamily(config, seed)` が CaseFamily を返す。`expandFamily` は表記・語彙・view 別の case を返すが、基礎 AST を変更しない。

- [ ] 同じ seed で byte-identical な dataset が得られるテストを作る。
- [ ] 5 表記の gold 一致、名前・値の一貫性、nonce 配置の均等性をテストする。
- [ ] before / after の正解が異なること、sibling が回答に漏れないことをテストする。
- [ ] 第 5.5 節の 10,000 AST property test を実装する。
- [ ] `audit` が失敗した dataset は plan / run に渡せないようにする。
- [ ] `sbt "bench / Test / runMain locality.bench.BenchTests --suite generator"` を通す。

### Task 6 — T1 プロンプトと offline plan【P0】

**Files:**

```text
bench/src/main/scala/locality/bench/prompt/Examples.scala
bench/src/main/scala/locality/bench/prompt/PromptBuilder.scala
bench/src/main/scala/locality/bench/run/Protocol.scala
bench/src/main/scala/locality/bench/cli/Main.scala
configs/smoke.json
configs/pilot.json
configs/main.json
bench/src/test/scala/locality/bench/PromptTests.scala
```

**Interfaces:** PromptBuilder は case / examples / RenderSpec から LlmRequest を返す。gold は別オブジェクトにし、request serializer には渡さない。

- [ ] 各条件が同じ例示と正解を同じ順序で持つ golden test を作る。
- [ ] test prompt に未展開 placeholder、gold metadata、probe より後のコード、別条件のソースが含まれないテストを作る。
- [ ] `plan --preset smoke --phase p0` が 20、pilot の P0 が 720 件を生成するテストを作る。
- [ ] `plan` が HTTP transport を呼ばないことを、呼ばれると失敗する mock でテストする。
- [ ] 第 6 節のテンプレートと CLI を実装し、`--suite prompt` を通す。

### Task 7 — Runner、上限、再開【P0】

**Files:**

```text
bench/src/main/scala/locality/bench/run/Runner.scala
bench/src/main/scala/locality/bench/run/Scheduler.scala
bench/src/main/scala/locality/bench/run/Budget.scala
bench/src/main/scala/locality/bench/run/RunStore.scala
bench/src/main/scala/locality/bench/run/Preflight.scala
bench/src/test/scala/locality/bench/RunnerTests.scala
```

**Interfaces:** Runner は LlmClient / Clock / RunStore / Protocol / Limits を受け取る。mock と実 API client を同じ runner で使う。

- [ ] 20 試行のうち半分で中断し、resume で terminal trial を再送しないテストを作る。
- [ ] 不正回答を retry しないこと、429 は設定範囲で retry することをテストする。
- [ ] request 数・HTTP attempt 数・token 予約が concurrency=4 でも上限を破らないテストを作る。
- [ ] usage 不明の失敗と dispatch 後のクラッシュで、予約が消えないことをテストする。
- [ ] キャッシュ / resume と replicate が別概念で、replicate=2 なら 2 回生成することをテストする。
- [ ] protocol hash 不一致、file lock 競合、末尾 JSONL 破損の扱いをテストする。
- [ ] `--execute` なしでは count / generate とも送信しないことをテストする。
- [ ] Runner と preflight を実装し、`--suite runner` を通す。

### Task 8 — T1 採点と最小レポート【P0】

**Files:**

```text
bench/src/main/scala/locality/bench/score/Score.scala
bench/src/main/scala/locality/bench/score/LookupGrader.scala
bench/src/main/scala/locality/bench/report/Report.scala
bench/src/test/scala/locality/bench/ScoreTests.scala
bench/src/test/scala/locality/bench/ReportTests.scala
```

**Interfaces:** Grader は gold / LlmResponse から Score を返す。Score に outcome、strict correctness、補助採点、理由、evaluable / infrastructure missing を保持する。

- [ ] `5916` は正解、`answer: 5916` は主採点では形式違反、`8604` は誤答になるテストを作る。
- [ ] refusal / incomplete / API error / not-dispatched の分母が第 10 節と一致するテストを作る。
- [ ] P0 の paired difference を計算し、CI 未実装の段階では CI を捏造せず `not_computed` と表示する。
- [ ] 保存済み raw result からだけで scores / CSV / report を再生成できることをテストする。
- [ ] P0 全体の mock smoke を通す。P0 が完成してから live smoke を行う。

### Task 9 — T2 と T3【P1】

**Files:**

```text
bench/src/main/scala/locality/bench/prompt/AstTable.scala
bench/src/main/scala/locality/bench/score/StackGrader.scala
bench/src/main/scala/locality/bench/score/GenerationGrader.scala
bench/src/test/scala/locality/bench/TaskTests.scala
```

- [ ] T2 が同じ prefix を使い、active stack のみを回答するテストを作る。
- [ ] T3 の table と AST の往復、node ID を変えた同一 AST、文順違い、値違い、close-name 違いの採点をテストする。
- [ ] 5 表記それぞれの正解ソースで AST accuracy=1 になることを確認する。
- [ ] P1 smoke が 60 件、P1 pilot が 1,800 件になることを確認する。
- [ ] タスク別の max output token 設定が全表記へ等しく適用されることを確認する。

### Task 10 — 対応付き統計・freeze【P1】

**Files:**

```text
bench/src/main/scala/locality/bench/stats/PairedAnalysis.scala
bench/src/main/scala/locality/bench/stats/ClusterBootstrap.scala
bench/src/main/scala/locality/bench/run/Freeze.scala
bench/src/test/scala/locality/bench/StatsTests.scala
bench/src/test/scala/locality/bench/FreezeTests.scala
```

- [ ] B=`[1,0,1,0]`、D=`[1,1,0,1]` の 4 families で差が 0.25 になるテストを作る。
- [ ] 各 family の語彙・replicate を複製しても family 数が増えず、対応差も変わらないことをテストする。
- [ ] bootstrap の固定 seed 再現、stratum ごとの復元抽出、percentile の補間をテストする。
- [ ] 全差ゼロの退化、baseline error=0、正答ゼロ、片側 missing をテストする。
- [ ] freeze 前の main live 実行を拒否し、freeze 後の prompt / protocol 変更も拒否するテストを作る。
- [ ] `--suite stats`、`--suite freeze`、最後に `sbt test` を通す。

### Task 11 — 操作手順と仕上げ【P1】

**Files:**

```text
README.md
AGENTS.md
CLAUDE.md
docs/protocol.md
docs/api-notes.md
docs/limitations.md
```

- [ ] README に依存ゼロ方針、setup、オフライン実行、live smoke、pilot、resume、report の手順を書く。
- [ ] AGENTS.md と CLAUDE.md には、不変条件と本指示書 / protocol の参照を書く。二つの内容が食い違わないようにする。
- [ ] 実装時に確認した API docs の日付、パラメータ、capability probe 結果を api-notes に残す。
- [ ] fixture / テスト出力と本物の API 結果を明確に分離する。mock は `synthetic_mock=true` と表示する。
- [ ] クリーンな作業ディレクトリ相当の状態で、offline smoke → audit → mock run → score → report を最初から実行する。
- [ ] 実行したコマンドと結果、実 API を実行したかどうか、未実行の段階を最後に報告する。

## 14. CLI の契約と操作例

以下の操作が動くように実装する。任意のフラグを「想定上のコマンド」のまま README に載せて終わらない。

### 14.1 共通ルール

`--preset` は smoke / pilot / main。`--phase` は p0 / p1。主実験 main は T1 / after_close の 5,120 件が初期設定。

run の計画段階で model を解決し、manifest に保存する。優先順位は、明示的な `--model`、`OPENAI_MODEL`、preset config、既定 `gpt-5.6-terra` の順。resume 時は保存済み model を使い、矛盾する override は拒否する。

全コマンドで `--help` を提供する。unknown option、未知の config key、不正な負数、未対応 enum は非ゼロ終了とし、無視しない。

### 14.2 最初は完全オフライン

```bash
sbt test
sbt "bench / run plan --preset smoke --phase p0 --out runs/smoke-offline"
sbt "bench / run audit --run runs/smoke-offline"
sbt "bench / run run --run runs/smoke-offline --mock"
sbt "bench / run score --run runs/smoke-offline"
sbt "bench / run report --run runs/smoke-offline"
```

mock client は fixture を返す。オラクルの正解を返すモードを作る場合は配線確認だけに用い、mock 専用コード以外が gold にアクセスしないことを検査する。モデル性能を再現したテストと呼ばない。

### 14.3 実 API の preflight

```bash
export OPENAI_MODEL=gpt-5.6-terra
# OPENAI_API_KEY はローカル環境へ安全に設定済みとする。
# terminal やログに値を echo しない。

sbt "bench / run preflight --out runs/preflight-terra --execute --max-http-attempts 4 --local-token-cap 20000"
```

同じモデル設定で、小さな token-count と生成を確認する。unsupported parameter、認証、project、モデルアクセス、応答 schema が分かれば記録し、推測で大量実行へ進まない。

### 14.4 P0 live smoke

```bash
sbt "bench / run plan --preset smoke --phase p0 --out runs/smoke-terra"
sbt "bench / run audit --run runs/smoke-terra"
sbt "bench / run run --run runs/smoke-terra --execute --max-generation-calls 20 --max-http-attempts 80 --local-token-cap 250000"
sbt "bench / run score --run runs/smoke-terra"
sbt "bench / run report --run runs/smoke-terra"
```

20 件が完了したかを件数で確認する。予算や障害で未完了なら、完了したように報告しない。

### 14.5 Pilot は小分けに開始

```bash
sbt "bench / run plan --preset pilot --phase p0 --out runs/pilot-terra"
sbt "bench / run audit --run runs/pilot-terra"
sbt "bench / run run --run runs/pilot-terra --execute --max-generation-calls 200 --max-http-attempts 600 --local-token-cap 1000000"

sbt "bench / run resume --run runs/pilot-terra --execute --max-generation-calls 200 --max-http-attempts 600 --local-token-cap 1000000"
sbt "bench / run score --run runs/pilot-terra"
sbt "bench / run report --run runs/pilot-terra"
```

CLI の `max-generation-calls / max-http-attempts` は**その起動で追加できる上限**。`local-token-cap` は、永続化した同じ budget window 内での累積上限とする。上の resume は同じ window で使えば、前回の使用量を引き継ぐ。

budget window は UTC 日付とするが、未解決要求の予約は日付が変わっても勝手に消さない。古い日付の未解決予約は別枠の不確定使用量として持ち越して診断表示する。当日の累積と混同せず、送信再開には解決または明示的な追加予算の承認を必要とする。

同じ日の途中で cap を引き上げる場合は、引き上げた事実と新旧値を events に記録する。他のアプリの使用量はこの台帳からは分からない。

### 14.6 主実験

```bash
sbt "bench / run plan --preset main --phase p1 --out runs/main-terra"
sbt "bench / run audit --run runs/main-terra"
sbt "bench / run freeze --run runs/main-terra --capabilities runs/preflight-terra/capabilities.json"
```

freeze が出力した protocol hash を使って実行する。

```bash
sbt "bench / run run --run runs/main-terra --execute --protocol-hash ACTUAL_HASH_FROM_FREEZE --max-generation-calls 200 --max-http-attempts 600 --local-token-cap 1000000"
```

`ACTUAL_HASH_FROM_FREEZE` は説明用であり、実際には freeze が返した値に置き換える。存在しない hash や未凍結 plan を受理しない。

## 15. Definition of Done

### P0 完了

- 外部 SDK / HTTP / JSON / parser / 統計ライブラリなしで compile / test できる。
- 5 表記の round trip と 10,000 AST のオラクル監査が通る。
- T1 の同一ケースで、表記間の正解一致が機械的に保証される。
- API キーなしで offline plan / audit / mock run / score / report が完走する。
- HTTP / API / retry / 予算 / resume の異常系テストが通る。
- live 用コマンドがあり、未実行なら未実行と明記する。

### P1 完了

- T2 / T3 を含む 60 件の smoke と 1,800 件の pilot の計画が正しく生成される。
- 対応付き cluster bootstrap と退化・欠測処理が通る。
- main の freeze / hash 検証と 5,120 件の計画が動く。
- raw result から、API 再呼び出しなしで全表とレポートを再生成できる。
- 実装済み、mock 検証済み、実 API 検証済み、主実験実施済みの状態を分けて報告する。

最終報告では「実験基盤が完成した」と「仮説が支持された」を区別する。結果の向きに関係なく、再現可能な正しい実験ができれば、この実装タスクは成功である。

## 16. 参照資料と確認事項

以下は実装上の事実確認に用いた一次資料。実験デザイン自体は本指示書の提案であり、これらの資料が文法の優劣を実証しているわけではない。公式資料の確認日は 2026-10-05。

[1] OpenAI, **GPT-5.6 Terra model**。model ID、Responses 対応、reasoning effort、snapshot / alias の確認先。
`https://developers.openai.com/api/docs/models/gpt-5.6-terra`

[2] OpenAI, **Create a model response**。Responses request / response schema、output item、status、store、truncation の確認先。
`https://developers.openai.com/api/reference/resources/responses/methods/create`

[3] OpenAI, **Reasoning models**。reasoning token と output token budget / usage の確認先。
`https://developers.openai.com/api/docs/guides/reasoning`

[4] OpenAI Help Center, **Sharing feedback, evaluation and fine-tuning data, and API inputs and outputs with OpenAI**。対象モデル、共有 project、利用階層別の枠、日次更新、課金境界の確認先。利用者個別の残枠を示すものではない。
`https://help.openai.com/en/articles/10306912-sharing-feedback-evaluation-and-fine-tuning-data-and-api-inputs-and-outputs-with-openai`

[5] OpenAI, **API Overview**。認証、project / organization header、request ID の確認先。
`https://developers.openai.com/api/reference/overview`

[6] Oracle, **Java SE 21: java.net.http.HttpClient**。JDK 標準 HTTP client、接続再利用、同期送信、リソース解放の確認先。
`https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html`

[7] OpenAI, **Counting tokens**。`POST /v1/responses/input_tokens` の request と返却値の確認先。
`https://developers.openai.com/api/docs/guides/token-counting`

[8] OpenAI, **Rate limits**。Retry-After、backoff、再試行対象外の quota / billing error の確認先。
`https://developers.openai.com/api/docs/guides/rate-limits`

[9] Scala, **Install / releases**、sbt, **1.10.7 release**。本指示書の固定バージョンは「最新であること」ではなく、実験を再現できることを優先した選択。
`https://www.scala-lang.org/download/`
`https://github.com/sbt/sbt/releases/tag/v1.10.7`

---

## エージェントへの最終指示

まず Task 1〜8 を実装し、P0 のオフライン smoke を完走させる。API キーと実行許可・上限が与えられている場合のみ preflight と live smoke を行う。次に Task 9〜11 を完成させ、pilot の結果をもとに主実験の計画を確定する。

不明な API 仕様は公式資料と小さな実リクエストで確認する。仮説を勝たせるのではなく、**ラベル付き終端が「読むときの助け」と「書くときの負担」をそれぞれどう変えるかを、同じ問題・同じ意味に対して測れる系**を作ること。
