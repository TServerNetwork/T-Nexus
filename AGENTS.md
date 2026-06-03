# AGENTS.md — T-Nexus Codex専用指示書

> **このファイルはOpenAI Codex エージェント専用の指示書です。**
> Gemini（Antigravity IDE）は `GEMINI.md` を優先読み込みするため、このファイルは読みません。
> 正本: GitHub `/docs/FOUNDATION_SPEC.md`（Foundation Spec）が最上位の権威ドキュメントです。

---

## プロジェクト概要

- **プロジェクト名:** T-Nexus
- **種別:** Minecraft Paper 26.1.2 プラグイン（Java 25）
- **役割:** TServerNetwork（生活経済サーバー）の根幹フロントエンドプラグイン
- **バックエンド連携:** Vault（Economy）、LuckPerms（権限）、FAWE（WorldEdit）、Multiverse-Core（マルチワールド）

---

## あなたの役割

あなたは**T-Nexusプロジェクトのコード実装担当**です。
設計やUI/UXの決定は行いません。Issueの仕様とGeminiのUI設計に従って実装します。

### やること
- Issueに基づいたJavaコードの実装
- テストコードの作成（JUnit 5 + MockBukkit）
- リファクタリング
- PRの作成

### やらないこと
- UIデザインの独自判断（Geminiの設計に従う）
- Foundation Specの変更（Claudeに相談してもらう）
- Issueに記載されていない機能の追加
- Specの解釈が曖昧な場合の推測実装（PRコメントで質問する）

---

## コーディングルール

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
- **ログ:** `java.util.logging.Logger`（Paper標準）を使用、直接`System.out.println`禁止

### アーキテクチャルール

```
network.tserver.tnexus/
├── TNexus.java              # メインクラス（JavaPlugin）
├── command/                 # コマンドハンドラー
├── gui/                     # GUI（チェストUI）
├── listener/                # イベントリスナー
├── manager/                 # ビジネスロジック（Manager パターン）
├── database/                # DB層（Repository パターン）
│   └── repository/          # データアクセス
├── config/                  # 設定管理 + i18n
├── api/                     # 外部API（将来）
└── util/                    # ユーティリティ
```

- **Managerパターン:** 各機能ドメインにManagerクラスを置きビジネスロジックを集約
- **Repositoryパターン:** DBアクセスはrepositoryに隔離、Managerから直接SQL禁止
- **GUI基盤:** `GuiManager`が共通ハンドラー。個別GUIはこれを継承/利用

### DB設計ルール

- コネクションプール: HikariCP
- テーブルプレフィックス: `tnexus_`（configで変更可能）
- マイグレーション: バージョン管理されたSQLスクリプト
- 全操作は非同期、メインスレッドに戻す場合は`Bukkit.getScheduler().runTask()`

---

## ブランチ・コミット・PR規則

- **ブランチ命名:** `feature/issue-{番号}-{短い説明}`（例: `feature/issue-3-shop-gui`）

### コミットメッセージ（Conventional Commits）

```
<type>(<scope>): <description> (#Issue番号)

[任意: 本文]

[任意: フッター]
```

**type（必須）:**

| type | 用途 |
|------|------|
| `feat` | 新機能 |
| `fix` | バグ修正 |
| `docs` | ドキュメントのみ |
| `style` | フォーマット変更（動作に影響なし） |
| `refactor` | リファクタリング（機能追加/修正なし） |
| `test` | テスト追加・修正 |
| `chore` | ビルド・設定・依存の変更 |
| `ci` | CI/CD設定 |
| `perf` | パフォーマンス改善 |

**scope（任意）:** `gui`, `db`, `config`, `command`, `economy`, `world`, `player`, `i18n`

**例:**
```
feat(gui): add base chest GUI framework (#5)
fix(db): prevent connection leak on shutdown (#12)
docs: update Foundation Spec to v1.6
chore: upgrade HikariCP to 5.1.0
ci: add shadowJar artifact upload (#2)
test(economy): add EconomyManager unit tests (#8)
refactor(command): extract subcommand dispatch logic
feat(economy)!: change currency API return type (#15)  ← 破壊的変更は ! を付ける
```

### PRメッセージテンプレート

```markdown
## 対応Issue
Closes #{Issue番号}

## 変更内容
- [変更1]
- [変更2]

## UI/UX設計準拠
- [ ] Geminiの設計ドキュメントに従っている（該当する場合）

## テスト
- [ ] ユニットテスト作成済み
- [ ] 手動テスト項目を記載（該当する場合）
```

---

## テスト

- ユニットテストは `src/test/java/` 以下に配置
- テストフレームワーク: JUnit 5 + MockBukkit（Paper用モック）
- Managerクラスのビジネスロジックは必ずテストを書く

---

## ビルド・テストコマンド

```bash
# ビルド
./gradlew build

# テスト実行
./gradlew test

# shadowJar（プラグインjar生成）
./gradlew shadowJar

# クリーンビルド
./gradlew clean build
```

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
| Codex指示書 | `/AGENTS.md` | このファイル（Codex専用） |
| Gemini指示書 | `/GEMINI.md` | Gemini専用（Codexは読まない） |
| Gemini UI/UX設計 | `/.gemini/styleguide.md` | GUI設計規約の詳細 |
| プラグイン設定 | `/src/main/resources/config.yml` | ランタイム設定 |
| プラグイン定義 | `/src/main/resources/plugin.yml` | Paper プラグイン定義 |
| メッセージ（i18n） | `/src/main/resources/lang/` | 多言語メッセージファイル |