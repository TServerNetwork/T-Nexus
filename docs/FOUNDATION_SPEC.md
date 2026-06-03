# T-Nexus Foundation Spec v1.0

> **このドキュメントは「前提指示書」です。**
> LLMとの会話が変わっても、このドキュメントを貼り付ければプロジェクト文脈が完全に復元されます。
> 全LLM（Claude / ChatGPT Codex / Gemini）は、このドキュメントに矛盾する指示を受けた場合、このドキュメントを優先してください。

---

## 1. プロジェクト概要

| 項目 | 内容 |
|------|------|
| プロジェクト名 | **T-Nexus** |
| 種別 | Minecraft Paper プラグイン (Java) |
| サーバー名 | **TServerNetwork** |
| サーバージャンル | 生活/経済系 |
| MCバージョン | **26.1.2**（Java Edition, Tiny Takeover） |
| Java バージョン | **Java 25**（LTS, 26.1必須） |
| 想定規模 | 〜30人（小規模コミュニティ） |
| ホスティング | 自宅サーバー → 人数増加時にレンタルVPSへ移行 |

### T-Nexusとは

TServerNetworkの**根幹プラグイン**。バックエンドプラグイン（Economy系、FAWE、Multiverse等）のAPIを統合し、プレイヤーが触れる**フロントエンドUI**を一元提供する。必要な箇所では独自にDBを持ちデータを管理する。

---

## 2. マルチワールド構成

| ワールド | 用途 | 備考 |
|----------|------|------|
| `lobby` | ロビー / ハブ | サーバー参加時の初期スポーン、ワールド選択UI |
| `master` | メインワールド | サーバーの中心、経済活動の拠点 |
| `life` | 生活用ワールド | 建築・生活特化 |
| `resource` | 資源ワールド | 定期リセット対象、採掘・素材収集用 |
| `pvp` | PvP / ミニゲーム | 戦闘・ミニゲーム用の隔離ワールド |

---

## 3. バックエンドプラグイン構成

T-Nexusが**API経由で連携する**プラグイン群。T-Nexus自身はこれらを直接改変しない。

| 機能 | プラグイン | 連携方法 |
|------|-----------|----------|
| 権限管理 | **LuckPerms** | LuckPerms API（ランク/権限の読み書き） |
| 経済 | **Vault** + 実体未定 | Vault API（通貨操作）。T-Nexusが独自Vault Providerを実装する可能性あり |
| ワールド編集 | **FAWE**（FastAsyncWorldEdit） | WorldEdit API |
| マルチワールド | **Multiverse-Core** | Multiverse API（ワールド作成/TP/管理） |

### Economy方針

Vault APIをフロントエンドの抽象レイヤーとして使用する。バックエンド実体（EssentialsX Economy / 自前実装等）は後で決定する。T-Nexusのコードは **Vault API のみに依存** し、バックエンド実体を直接参照しないこと。

---

## 4. 機能スコープと優先順位

### Phase 1: 経済系UI（最優先）
- ショップシステム（GUI売買）
- プレイヤー間取引
- 通貨表示・残高確認
- ショップ作成・管理（管理者向け）

### Phase 2: ワールド管理UI
- ワールド選択メニュー（GUI）
- テレポート管理
- ワールド情報表示
- ワープポイント設定

### Phase 3: プレイヤー管理
- ランク表示・昇格UI
- 権限管理（LuckPerms連携）
- プレイヤー統計（プレイ時間、経済活動等）
- 管理者ダッシュボード

### Phase 4: カスタムコンテンツ
- クエストシステム
- イベントシステム
- 実績・報酬

---

## 5. 技術スタック

```
言語:           Java（Java 25）
サーバー:        Paper 26.1.2
ビルドツール:     Gradle (Kotlin DSL)
DB:             MySQL / MariaDB
Config形式:     YAML
バージョン管理:   GitHub
CI/CD:          GitHub Actions
多言語対応:      初期は日本語、後々多言語対応（i18n構造を最初から設計に含める）
```

### 開発環境

```
メインIDE:       Google Antigravity IDE（VS Codeフォーク、Gemini 3エージェント内蔵）
IDE（補助）:     IntelliJ IDEA 等（初期セットアップ、依存管理、重いリファクタリング時のみ）
```

**Antigravity IDEを主軸とする理由:** Antigravityはagent-first IDEであり、Gemini 3 Proエージェントがネイティブ統合されている。VS Codeの拡張機能もそのまま動作するため、既存のJava開発ワークフローを維持しつつ、AIエージェントの自律的な開発支援を最大限に活用できる。

**Antigravity IDE 必須拡張機能（VS Code互換）:**
- Extension Pack for Java（Microsoft）
- Gradle for Java（Microsoft）
- GitHub Pull Requests
- GitLens（推奨）

> **注記:** Gemini Code Assist拡張機能は不要。Antigravity IDEにはGemini 3エージェントがネイティブ内蔵されている。

**IDEを使う場面（例外的）:**
- Gradleプロジェクトの初期生成・インポートで問題が出た場合
- 依存関係の解決が複雑な場合のGUI操作
- 大規模リファクタリング（Rename Symbol等がAntigravityで不十分な場合）

### Gradle 構成方針

- `build.gradle.kts`（Kotlin DSL）
- shadowJar でプラグインjarにDB driverなど必要な依存を同梱
- Paper公式のpaper-pluginテンプレート準拠
- Java 25 toolchain 指定

### DB 設計方針

- 接続先はconfig.ymlで切り替え可能にする（MySQL接続情報）
- HikariCP でコネクションプーリング
- テーブルプレフィックスをconfigで設定可能に（`tnexus_`）
- マイグレーション戦略: バージョン管理されたSQLスクリプト or Flyway軽量版
- **非同期DB操作**: メインスレッドを絶対にブロックしない（BukkitScheduler / CompletableFuture）

---

## 6. アーキテクチャ設計

### パッケージ構成

```
network.tserver.tnexus/
├── TNexus.java                  # メインクラス（JavaPlugin）
├── command/                     # コマンドハンドラー
│   ├── economy/
│   ├── world/
│   ├── player/
│   └── admin/
├── gui/                         # GUI（チェストUI）
│   ├── GuiManager.java          # GUI基盤・共通ハンドラー
│   ├── economy/
│   ├── world/
│   └── player/
├── listener/                    # イベントリスナー
├── manager/                     # ビジネスロジック
│   ├── EconomyManager.java      # Vault API ラッパー
│   ├── WorldManager.java        # Multiverse API ラッパー
│   ├── PermissionManager.java   # LuckPerms API ラッパー
│   └── PlayerDataManager.java   # 独自データ管理
├── database/                    # DB層
│   ├── DatabaseManager.java     # 接続管理（HikariCP）
│   ├── repository/              # データアクセス
│   └── migration/               # マイグレーション
├── config/                      # 設定管理
│   ├── ConfigManager.java
│   └── MessageConfig.java       # i18n対応メッセージ
├── api/                         # 外部プラグイン向けAPI（将来）
└── util/                        # ユーティリティ
```

### 設計原則

1. **Manager パターン**: 各機能ドメインにManagerクラスを置き、ビジネスロジックを集約
2. **Repository パターン**: DBアクセスはrepositoryに隔離し、Managerから直接SQLを書かない
3. **非同期優先**: DB操作、重い処理は必ず非同期。UIの更新はメインスレッドに戻す
4. **Vault API 抽象化**: Economy実体への直接依存を禁止
5. **Config駆動**: ハードコードを避け、YAML設定で挙動を変更可能にする
6. **i18n Ready**: メッセージは全てMessageConfigから取得。ロケールファイル差し替えで言語切替

---

## 7. GUI設計方針

### 基本方針
- **チェストUI（Inventory GUI）を主体**とする
- 補助的にチャットメッセージ（クリッカブルテキスト）とScoreboardを活用
- Scoreboardは常時表示情報（残高、現在ワールド等）に使用

### GUI基盤要件
- ページネーション対応（アイテム一覧等）
- 戻るボタン、閉じるボタンの統一配置
- アニメーション/装飾アイテムの統一スタイル
- クリックイベントのデバウンス（二重クリック防止）
- GUI操作中のアイテム持ち逃げ防止

---

## 8. LLM開発ワークフロー

### 役割分担

| LLM | ツール形態 | 役割 | 担当領域 |
|-----|-----------|------|----------|
| **Claude** | claude.ai（ブラウザ） | 司令塔 | 設計、Issue作成、コードレビュー、最終評価、ドキュメント管理 |
| **Codex** | Codex – OpenAI's coding agent | コード開発 | Java実装、テスト作成、リファクタリング |
| **Gemini** | Antigravity IDE 内蔵エージェント（Gemini 3 Pro） | UI/UX担当 | GUI設計、UXフロー、ビジュアルデザイン提案 |

### ツール連携の構図

```
┌──────────────────────────────────────────────────────┐
│  開発者のワークステーション                               │
│                                                        │
│  ┌───────────────────────────────────────────────┐    │
│  │  Antigravity IDE（主戦場 / VS Codeフォーク）     │    │
│  │                                                 │    │
│  │  ├── Gemini 3 Pro Agent（ネイティブ内蔵）        │    │
│  │  │   └── UI/UX設計・コード提案・エージェント実行   │    │
│  │  │                                              │    │
│  │  ├── ソースコード編集・デバッグ                    │    │
│  │  ├── VS Code拡張機能（Java, Gradle等）           │    │
│  │  └── Git操作・PR管理                             │    │
│  └───────────────────────────────────────────────┘    │
│                                                        │
│  ┌──────────────┐    ┌──────────────────────┐         │
│  │ Claude (Web)  │    │  Codex (OpenAI)      │         │
│  │ 司令塔        │    │  コーディングエージェント │         │
│  │ 設計/評価/Spec │    │  実装/テスト/リファクタ  │         │
│  └──────┬───────┘    └──────────┬───────────┘         │
│         │                       │                       │
│         └───────┬───────────────┘                       │
│                 ▼                                        │
│          GitHub（リポジトリ + Actions）                   │
└──────────────────────────────────────────────────────┘
```

### 各ツールの操作フロー

**Claude（司令塔）:**
- ブラウザでclaude.aiを使用
- Foundation Specの管理・改訂
- Issue要件の作成 → 開発者がGitHubに転記
- 完成物のレビュー・最終評価

**Codex（コード開発）:**
- OpenAIのコーディングエージェントとして動作
- GitHubリポジトリに直接アクセスし、Issueの仕様に基づいてコードを生成
- PRを作成 → 開発者がレビュー・マージ

**Gemini（UI/UX — Antigravity IDE内蔵）:**
- Antigravity IDEのネイティブエージェントとして動作（別途拡張不要）
- 開発者がエディタ上でUI/UXに関する相談・設計を行う
- Agent Mode / Editor Modeを使い分けて自律的にコード修正も可能
- `.gemini/styleguide.md` と `AGENTS.md` を自動読み込み

### 開発サイクル（1機能あたり）

```
1. [Claude + 開発者]        → Issue作成（要件定義、受け入れ条件）
2. [Gemini + 開発者]        → Antigravity IDE内でUI/UX設計・選考
3. [Codex + 開発者]         → 実装（Issue仕様 + GeminiのUI設計に従う）→ PR作成
4. [GitHub Actions]         → 自動ビルド → jarダウンロード → テストサーバーで動作確認
5. [Gemini + 開発者]        → Antigravity IDE内でUI/UX改良フィードバック
6. [Codex + 開発者]         → 修正実装 → PR更新
7. 4-6を繰り返し → 完成
8. [Claude + 開発者]        → 最終評価 → Issueクローズ
```

### 各LLMへの指示ルール

**エージェント設定ファイル（リポジトリに同梱、自動読み込み）:**

| ファイル | 読み込み対象 | 内容 |
|---------|------------|------|
| `/AGENTS.md` | **Codex専用** | 共通コーディングルール + Codex固有ルール（実装・テスト・PR規則） |
| `/GEMINI.md` | **Gemini専用** | 共通コーディングルール + Gemini固有ルール（UI/UX担当指示） |
| `/.gemini/styleguide.md` | Gemini（参照） | UI/UX設計規約の詳細（GUIテンプレート、デザイントークン） |
| `/.gemini/config.yaml` | Gemini（参照） | コードレビュー設定、ignore patterns |

> **排他的読み込みの仕組み:**
> Gemini/Antigravityはルールファイルを優先順位制で読み込む（`GEMINI.md` > `.gemini/styleguide.md` > `AGENTS.md`）。
> `GEMINI.md` が存在するため、GeminiはAGENTS.mdを読まない。
> Codexは`AGENTS.md`のみを読み、`GEMINI.md`は読まない。
> これにより各エージェントが自分専用の指示だけを受け取る。

**共通ルール（両方のファイルに記載済み）:**
- Foundation Specに記載された技術スタックに従うこと
- パッケージ構成を勝手に変更しないこと
- 新規パッケージ/依存追加が必要な場合はIssueで提案すること
- Vault API以外のEconomy実体への直接依存を作らないこと
- メインスレッドでDB操作を行わないこと

**Codex向け追加ルール（`/AGENTS.md` に記載）:**
- Issueに書かれた仕様とGeminiのUI設計に厳密に従う
- 独自判断での機能追加/変更は禁止（提案はIssueで行う）
- Javaコード規約: Google Java Style Guide ベース
- テストコードも合わせて作成
- PRの説明に「どのIssueに対応するか」「変更の概要」を必ず記載
- ブランチ命名規則: `feature/issue-{番号}-{短い説明}` （例: `feature/issue-3-shop-gui`）
- コミットメッセージ: **Conventional Commits** 準拠 — `<type>(<scope>): <description> (#Issue番号)`

**Gemini向け追加ルール（`/GEMINI.md` + `/.gemini/styleguide.md` に記載）:**
- チェストUI設計は9x行のグリッドレイアウトで提案
- 使用アイテムのマテリアル名（Material enum）を明記
- チャットメッセージのフォーマット/色指定も含める
- Scoreboardレイアウトは16行制限を考慮
- Antigravity IDE内での作業が前提。設計はコード内コメントやMarkdownで記録

---

## 9. config.yml 基本構造

```yaml
# T-Nexus Configuration
tnexus:
  # データベース設定
  database:
    host: "localhost"
    port: 3306
    name: "tnexus"
    username: "root"
    password: ""
    table-prefix: "tnexus_"
    pool-size: 10

  # サーバー設定
  server:
    default-world: "lobby"
    resource-world: "resource"

  # 経済設定
  economy:
    currency-name: "コイン"
    currency-symbol: "¥"
    starting-balance: 1000.0
    max-balance: 999999999.0

  # GUI設定
  gui:
    main-menu-title: "&6&lT-Nexus メニュー"
    items-per-page: 45
    border-item: "GRAY_STAINED_GLASS_PANE"
    back-button-slot: 45
    close-button-slot: 49
    next-page-slot: 53
    prev-page-slot: 46

  # 言語設定
  locale: "ja_JP"
```

---

## 10. plugin.yml（Paper plugin.yml）

```yaml
name: T-Nexus
version: "${version}"
main: network.tserver.tnexus.TNexus
api-version: "26.1"
description: "TServerNetwork Core Frontend Plugin"
author: TServer
website: ""

depend:
  - Vault
  - LuckPerms
  - Multiverse-Core

softdepend:
  - FastAsyncWorldEdit

commands:
  tnexus:
    description: "T-Nexus メインコマンド"
    usage: "/<command>"
    aliases: [tn, nexus]

permissions:
  tnexus.admin:
    description: "T-Nexus 管理者権限"
    default: op
  tnexus.use:
    description: "T-Nexus 基本使用権限"
    default: true
```

---

## 11. 開発ロードマップ

```
[Phase 0] プロジェクト基盤構築
  ├── Gradle プロジェクトセットアップ
  ├── Paper 26.1.2 開発環境構築
  ├── GitHub リポジトリ作成
  ├── GitHub Actions CI/CD パイプライン
  ├── DB接続基盤（HikariCP + MySQL）
  ├── Config管理基盤
  ├── GUI基盤フレームワーク
  ├── i18nメッセージ基盤
  └── メインコマンド (/tnexus) スケルトン

[Phase 1] 経済系UI
[Phase 2] ワールド管理UI
[Phase 3] プレイヤー管理
[Phase 4] カスタムコンテンツ
```

---

## 12. バージョニング

- **セマンティックバージョニング**: `MAJOR.MINOR.PATCH`
- 初期開発: `0.1.0` から開始
- Phase完了ごとにMINOR++
- `1.0.0` = Phase 1-3 完了の安定版

---

## 13. Spec改訂・周知プロトコル（法改正ルール）

LLMベース開発では**全LLMが同じ版のSpecを参照していること**が絶対条件である。
Spec改訂時は以下のプロトコルに従う。

### 13.1 改訂の分類

| 分類 | 影響度 | 例 | Specバージョン変更 |
|------|--------|----|--------------------|
| **MAJOR** | 全LLMに影響。進行中タスクの中断判断が必要 | アーキテクチャ変更、パッケージ構成変更、依存プラグイン追加/削除、開発言語変更 | `vX.0` |
| **MINOR** | 特定LLMに影響。進行中タスクは完了後に適用可 | 新Phase追加、GUI方針変更、DB設計変更、config構造変更 | `vX.Y` |
| **PATCH** | 軽微。即時適用可 | 誤字修正、補足説明追加、サンプル値変更 | `vX.Y.Z` |

### 13.2 改訂フロー

```
1. [開発者 or Claude] Specの変更を提案
2. [Claude]           改訂内容を整理し、影響範囲を判定（MAJOR/MINOR/PATCH）
3. [Claude]           Specドキュメントを更新し、改訂履歴に記録
4. [Claude]           変更サマリー（Changelog Briefing）を生成 ← 下記フォーマット
5. [開発者]           各LLMの新規会話 or 既存会話にChangelog Briefingを貼り付け
6. [各LLM]            Briefingを確認し「了解」を返答（確認応答）
7. [開発者]           全LLMの確認応答を確認 → 開発続行
```

### 13.3 Changelog Briefing フォーマット

各LLMに渡す改訂通知テンプレート。Spec全文の再送を不要にし、差分だけで同期を取る。

```markdown
---
## ⚠️ T-Nexus Foundation Spec 改訂通知
**改訂:** vX.Y → vX.Z
**日付:** YYYY-MM-DD
**分類:** MAJOR / MINOR / PATCH

### 変更内容
- [変更1の概要]
- [変更2の概要]

### 影響を受けるセクション
- セクション X: [変更の要約]
- セクション Y: [変更の要約]

### 進行中タスクへの影響
- [影響の有無と対応方針]

### 確認事項
この改訂内容を理解した場合、以下を返答してください:
「T-Nexus Spec vX.Z 確認完了。[影響を受ける進行中タスクがあればその対応方針]」
---
```

### 13.4 コンフリクト解決ルール

| 状況 | 対応 |
|------|------|
| LLMが古い版のSpecに基づいてコードを生成した | Briefing送付 → 影響箇所を特定 → 修正指示 |
| 改訂内容と進行中Issueが矛盾する | MAJOR: タスク中断し再設計。MINOR: 現タスク完了後に適用 |
| LLMがSpecに無い判断をした場合 | Specに記載がない = 提案としてIssueに起票。勝手に実装しない |
| 複数LLMの出力が矛盾した場合 | Claudeが最終判断。Spec準拠の出力を正とする |

### 13.5 会話リセット時の復元手順

LLMとの会話が新規になった場合（コンテキストリセット）:

```
1. Foundation Spec 最新版の全文を貼り付け
2. 「あなたの役割は [Claude/Codex/Gemini] です。Spec vX.Y を確認してください」
3. LLMの確認応答を待つ
4. 進行中のIssue情報を貼り付け（該当する場合）
5. 開発続行
```

### 13.6 Specの保管場所

| 場所 | 用途 |
|------|------|
| **GitHub リポジトリ** `/docs/FOUNDATION_SPEC.md` | 正本（Single Source of Truth） |
| **GitHub Releases** | MAJORバージョンごとにタグ付け |
| **Claude メモリ** | 現在のSpecバージョンと主要決定事項のサマリー |

> **鉄則: GitHubの `/docs/FOUNDATION_SPEC.md` が唯一の正本である。**
> ローカルコピーやLLMのメモリと食い違った場合、GitHub上の版が正しい。

---

## 改訂履歴

| バージョン | 日付 | 分類 | 内容 |
|-----------|------|------|------|
| v1.0 | 2026-06-02 | - | 初版作成 |
| v1.1 | 2026-06-02 | MINOR | Spec改訂・周知プロトコル（セクション13）追加 |
| v1.2 | 2026-06-02 | MINOR | 開発環境（VS Code主軸）、LLMツール形態（Codex agent / Gemini Code Assist）明記、ツール連携図追加 |
| v1.3 | 2026-06-02 | MINOR | エージェント設定ファイル（AGENTS.md / .gemini/styleguide.md / .gemini/config.yaml）追加、Spec内にファイル参照を記載 |
| v1.4 | 2026-06-02 | MINOR | メインIDE: VS Code → Antigravity IDE（VS Codeフォーク、Gemini 3内蔵）に変更。Gemini Code Assist → Antigravity内蔵エージェントに統一 |
| v1.5 | 2026-06-03 | MINOR | エージェント指示ファイルを排他的に分離。AGENTS.md=Codex専用、GEMINI.md=Gemini専用（新規追加）。パッケージ名を network.tserver.tnexus に統一（ドメイン tserver.network 準拠）。GitHub組織: TServerNetwork |