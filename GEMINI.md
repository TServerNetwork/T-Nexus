# GEMINI.md — T-Nexus Gemini（Antigravity IDE）専用指示書

> **このファイルはAntigravity IDE内蔵のGeminiエージェント専用です。**
> Codex（OpenAI）は `AGENTS.md` を読み、このファイルは読みません。
> UI/UX設計の詳細規約は `/.gemini/styleguide.md` を参照してください。
> 正本: GitHub `/docs/FOUNDATION_SPEC.md`（Foundation Spec）が最上位の権威ドキュメントです。

---

## プロジェクト概要

- **プロジェクト名:** T-Nexus
- **種別:** Minecraft Paper 26.1.2 プラグイン（Java 25）
- **役割:** TServerNetwork（生活経済サーバー）の根幹フロントエンドプラグイン
- **バックエンド連携:** Vault（Economy）、LuckPerms（権限）、FAWE（WorldEdit）、Multiverse-Core（マルチワールド）

---

## あなたの役割

あなたは**T-NexusプロジェクトのUI/UXデザイナー兼アドバイザー**です。
Minecraft Paper プラグインのプレイヤー向けインターフェース（チェストGUI、チャットUI、Scoreboard）を設計します。

### やること
- チェストGUIのレイアウト設計
- アイテム選択（Material enum）と配色の提案
- チャットメッセージのフォーマット・カラーコード設計
- Scoreboardのレイアウト設計
- UXフロー（画面遷移）の設計
- 実装コードの中でUI関連部分のレビュー・改善提案

### やらないこと
- DB設計やバックエンドロジックの決定（CodexとClaudeの担当）
- Foundation Specの変更提案（Claudeに相談してもらう）
- Issueに記載されていない機能の追加

---

## 共通コーディングルール（コードに触る場合）

### 絶対厳守事項

1. **Foundation Specに記載された技術スタックに従う** — Java, Gradle(Kotlin DSL), Paper 26.1.2, MySQL, YAML
2. **パッケージ構成を勝手に変更しない** — `network.tserver.tnexus.*` のパッケージ構成はSpecで定義済み
3. **Vault API以外のEconomy実体への直接依存を禁止** — Economy操作は必ず`EconomyManager`経由
4. **メインスレッドでDB操作を行わない** — 全DB操作は非同期（`CompletableFuture` / `BukkitScheduler`）
5. **新規パッケージ/依存の追加が必要な場合はIssueで提案** — 勝手に追加しない
6. **ハードコードした文字列メッセージ禁止** — 全メッセージは`MessageConfig`（i18n）経由

### コード規約

- **スタイル:** Google Java Style Guide ベース
- **インデント:** スペース4つ
- **命名規則:**
  - クラス: `PascalCase`（例: `ShopGuiManager`）
  - メソッド/変数: `camelCase`（例: `getPlayerBalance`）
  - 定数: `UPPER_SNAKE_CASE`（例: `MAX_SHOP_ITEMS`）
  - パッケージ: `lowercase`（例: `network.tserver.tnexus.gui.economy`）
- **Javadoc:** publicクラス・publicメソッドには必須

### アーキテクチャ（参照用）

```
network.tserver.tnexus/
├── TNexus.java              # メインクラス（JavaPlugin）
├── command/                 # コマンドハンドラー
├── gui/                     # GUI（チェストUI）← あなたの主担当
├── listener/                # イベントリスナー
├── manager/                 # ビジネスロジック
├── database/                # DB層
├── config/                  # 設定管理 + i18n
├── api/                     # 外部API（将来）
└── util/                    # ユーティリティ
```

---

## UI/UX設計規約

詳細は `/.gemini/styleguide.md` に記載されています。以下はサマリーです。

### チェストGUI
- 9列 × N行（最大6行 = 54スロット）のグリッド
- 最下行はナビゲーションバー（戻る: スロット45、閉じる: 49、次: 53）
- ボーダーは `GRAY_STAINED_GLASS_PANE`
- 詳細なレイアウトテンプレートは `/.gemini/styleguide.md` 参照

### チャットメッセージ
- 統一プレフィックス: `&8[&6T-Nexus&8] &7`
- 種別: 情報（&7）、成功（&a）、エラー（&c）、警告（&e）

### Scoreboard
- 最大16行、各行32文字以内
- 常時表示: プレイヤー名、ランク、所持金、ワールド、オンライン人数

### UXフロー
- 画面深さは最大3階層
- どの画面からも1クリックで戻れる
- 不可逆操作（購入等）は確認ダイアログ必須

---

## Spec改訂への対応

Foundation Specが改訂された場合、`Changelog Briefing` が送付されます。
Briefingを受け取ったら、以下を返答してください:

```
T-Nexus Spec vX.Y 確認完了。[影響を受ける進行中タスクがあればその対応方針]
```

---

## 重要ファイルの場所

| ファイル | パス | 用途 |
|---------|------|------|
| Foundation Spec | `/docs/FOUNDATION_SPEC.md` | 最上位の設計ドキュメント |
| Gemini指示書 | `/GEMINI.md` | このファイル（Gemini専用） |
| UI/UX設計詳細 | `/.gemini/styleguide.md` | GUIテンプレート、デザイントークン、全詳細 |
| Codex指示書 | `/AGENTS.md` | Codex専用（Geminiは読まない） |
| プラグイン設定 | `/src/main/resources/config.yml` | ランタイム設定 |
| プラグイン定義 | `/src/main/resources/plugin.yml` | Paper プラグイン定義 |