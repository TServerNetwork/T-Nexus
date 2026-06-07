# [0.21.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.20.0...v0.21.0) (2026-06-07)


### Features

* **player:** track entity damage stats ([#69](https://github.com/TServerNetwork/T-Nexus/issues/69)) ([8aca5ac](https://github.com/TServerNetwork/T-Nexus/commit/8aca5ac45bac2c73eb5c71ecd83c553e754f3420))

# [0.20.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.19.0...v0.20.0) (2026-06-06)


### Features

* **player:** track block stats ([#68](https://github.com/TServerNetwork/T-Nexus/issues/68)) ([3ace31d](https://github.com/TServerNetwork/T-Nexus/commit/3ace31df44840a90ac17e5c026436adabf9eda11))

# [0.19.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.18.0...v0.19.0) (2026-06-06)


### Features

* **player:** add movement distance stats ([#67](https://github.com/TServerNetwork/T-Nexus/issues/67)) ([1575538](https://github.com/TServerNetwork/T-Nexus/commit/1575538f8bc6bb1031dc820c528bb7b562cdf5d7))

# [0.18.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.17.0...v0.18.0) (2026-06-06)


### Features

* **player:** record death and respawn stats ([#66](https://github.com/TServerNetwork/T-Nexus/issues/66)) ([2012f7a](https://github.com/TServerNetwork/T-Nexus/commit/2012f7a37ed9c76d153393ad2c9d1c0cdb93cb83))

# [0.17.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.16.0...v0.17.0) (2026-06-06)


### Features

* **player:** track session stats ([#65](https://github.com/TServerNetwork/T-Nexus/issues/65)) ([7717c51](https://github.com/TServerNetwork/T-Nexus/commit/7717c5101dc4535f9b447b17918f39dd56c8702a))

# [0.16.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.15.0...v0.16.0) (2026-06-06)


### Features

* **db:** add stats schema migrations ([#64](https://github.com/TServerNetwork/T-Nexus/issues/64)) ([271d19c](https://github.com/TServerNetwork/T-Nexus/commit/271d19c1b33117b7c37d4fe212fea56d0674224e))

# [0.15.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.14.1...v0.15.0) (2026-06-06)


### Features

* **gui:** improve sign display formatting ([#58](https://github.com/TServerNetwork/T-Nexus/issues/58)) ([29d2e3d](https://github.com/TServerNetwork/T-Nexus/commit/29d2e3d9b2d0b8b3e0d5d9790b396630cfa948c3))

## [0.14.1](https://github.com/TServerNetwork/T-Nexus/compare/v0.14.0...v0.14.1) (2026-06-06)


### Bug Fixes

* **command:** show balance tab completions on empty input ([1f6b9ae](https://github.com/TServerNetwork/T-Nexus/commit/1f6b9ae5631df8bbcb6a6fb1bad81044c5f390ed))

# [0.14.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.13.2...v0.14.0) (2026-06-06)


### Features

* **command:** add tab completers for commands (#XX) ([cac3723](https://github.com/TServerNetwork/T-Nexus/commit/cac3723c2a97b5bb3cb27e2f3dbf22022a2a2f35)), closes [#XX](https://github.com/TServerNetwork/T-Nexus/issues/XX)

## [0.13.2](https://github.com/TServerNetwork/T-Nexus/compare/v0.13.1...v0.13.2) (2026-06-06)


### Bug Fixes

* **shop:** block hopper and dropper access to shop chests ([#59](https://github.com/TServerNetwork/T-Nexus/issues/59)) ([e2e3709](https://github.com/TServerNetwork/T-Nexus/commit/e2e37097202eba54a7fc6279928a475d2cc2c983))
* **shop:** unify ServerShop unavailable display with PlayerShop ([#59](https://github.com/TServerNetwork/T-Nexus/issues/59)) ([460644e](https://github.com/TServerNetwork/T-Nexus/commit/460644ebcc4f633e059a4f8c07389dec970c0653))

## [0.13.1](https://github.com/TServerNetwork/T-Nexus/compare/v0.13.0...v0.13.1) (2026-06-06)


### Bug Fixes

* **shop:** stop empty link chest access loop ([5367251](https://github.com/TServerNetwork/T-Nexus/commit/5367251bc9c22bb6ed8b71192653b94718f3dd67))

# [0.13.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.12.1...v0.13.0) (2026-06-06)


### Features

* **economy:** support multiple linked shop chests ([#54](https://github.com/TServerNetwork/T-Nexus/issues/54)) ([5d10742](https://github.com/TServerNetwork/T-Nexus/commit/5d10742163a464bb5465369d2edc43bd127f6b75))

## [0.12.1](https://github.com/TServerNetwork/T-Nexus/compare/v0.12.0...v0.12.1) (2026-06-06)


### Bug Fixes

* **shop:** correctly register link tool in banned-materials ([#53](https://github.com/TServerNetwork/T-Nexus/issues/53)) ([c620f49](https://github.com/TServerNetwork/T-Nexus/commit/c620f4934903e287c5ed043c5b1a52039cd6e26b))
* **shop:** prevent unauthorized players from opening shop chests ([#53](https://github.com/TServerNetwork/T-Nexus/issues/53)) ([5420362](https://github.com/TServerNetwork/T-Nexus/commit/5420362e1eea923ef1a8468c3d1c38f2f385e8dd))
* **shop:** skip GUI and show message when shop is unavailable ([#53](https://github.com/TServerNetwork/T-Nexus/issues/53)) ([64e9e05](https://github.com/TServerNetwork/T-Nexus/commit/64e9e05bec4dc1046d8edcfb1372d3f6c0f39cca))
* **shop:** update sign to red when both B and S are unavailable ([#53](https://github.com/TServerNetwork/T-Nexus/issues/53)) ([c797f85](https://github.com/TServerNetwork/T-Nexus/commit/c797f8531bcccda630fa85a45f5b4703b15e06f9))

# [0.12.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.11.1...v0.12.0) (2026-06-06)


### Features

* **shop:** add max trade quantities to invMax lore ([#50](https://github.com/TServerNetwork/T-Nexus/issues/50)) ([6394057](https://github.com/TServerNetwork/T-Nexus/commit/63940577e198d94061fb066a4e8aefa8aee12835))
* **shop:** gray out quantity buttons for unsupported trade type ([#50](https://github.com/TServerNetwork/T-Nexus/issues/50)) ([0a6c306](https://github.com/TServerNetwork/T-Nexus/commit/0a6c30666856a81b3ea9fc9bdbe90cdd1437b8b3))
* **shop:** improve grayout visibility for disabled buttons ([#50](https://github.com/TServerNetwork/T-Nexus/issues/50)) ([aa68212](https://github.com/TServerNetwork/T-Nexus/commit/aa6821280c4767e3defc9fb0e68648e6bf2efe75))

## [0.11.1](https://github.com/TServerNetwork/T-Nexus/compare/v0.11.0...v0.11.1) (2026-06-06)


### Bug Fixes

* **shop:** add link tool to banned-materials list ([#49](https://github.com/TServerNetwork/T-Nexus/issues/49)) ([e7225d4](https://github.com/TServerNetwork/T-Nexus/commit/e7225d4fb6dec2d9985d35df054cd91f2087aed4))
* **shop:** apply auto-adjust logic for insufficient funds and stock ([#49](https://github.com/TServerNetwork/T-Nexus/issues/49)) ([c2d3364](https://github.com/TServerNetwork/T-Nexus/commit/c2d33645057a78517fd1efe199bcec8f2b1487b2))
* **shop:** manage B/S availability independently ([#49](https://github.com/TServerNetwork/T-Nexus/issues/49)) ([44a7a03](https://github.com/TServerNetwork/T-Nexus/commit/44a7a0322ea44d2297b685b30078f80fbd4d30be))
* **shop:** show detailed unavailable messages by state ([#49](https://github.com/TServerNetwork/T-Nexus/issues/49)) ([07d5fb6](https://github.com/TServerNetwork/T-Nexus/commit/07d5fb691318ff2897b3c0502a84931d388ba950))

# [0.11.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.10.0...v0.11.0) (2026-06-06)


### Features

* **command:** add shop linkitem and admin balance tools ([#42](https://github.com/TServerNetwork/T-Nexus/issues/42)) ([048ceec](https://github.com/TServerNetwork/T-Nexus/commit/048ceeccdfbb882b82d1b91d2f75e10b6ff7389a))

# [0.10.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.9.0...v0.10.0) (2026-06-06)


### Features

* **gui:** improve sign shop interaction flow ([#41](https://github.com/TServerNetwork/T-Nexus/issues/41)) ([cc1289e](https://github.com/TServerNetwork/T-Nexus/commit/cc1289eb4d1c269b5b0a239e58eab18a25e996d8))

# [0.9.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.8.3...v0.9.0) (2026-06-05)


### Features

* **gui:** add showcase shop header ([#44](https://github.com/TServerNetwork/T-Nexus/issues/44)) ([758a9f1](https://github.com/TServerNetwork/T-Nexus/commit/758a9f1d5176f7ea369d5e7b8536647e5f0e96bb))

## [0.8.3](https://github.com/TServerNetwork/T-Nexus/compare/v0.8.2...v0.8.3) (2026-06-05)


### Bug Fixes

* **economy:** fix /pay confirm clickevent command ([b20f919](https://github.com/TServerNetwork/T-Nexus/commit/b20f919d932c9f8538e7ea0118156d0b783c7738))
* **shop:** apply color codes to sign text ([330c7f9](https://github.com/TServerNetwork/T-Nexus/commit/330c7f926ad85ea3631bca5f1a92bf7b73faaf1f))
* **shop:** cancel link mode on error ([ba97dec](https://github.com/TServerNetwork/T-Nexus/commit/ba97deca886faf1b09c76ca25ee4b41d30fd1e65))
* **shop:** release sign protection after shop deletion ([268077d](https://github.com/TServerNetwork/T-Nexus/commit/268077d3aa4f6d21b9abdaaa65b9721aa568407d))
* **shop:** ServerShop only requires chest at creation time ([405181d](https://github.com/TServerNetwork/T-Nexus/commit/405181d4585b7716704b3fed2667ab70163dc21e))

## [0.8.2](https://github.com/TServerNetwork/T-Nexus/compare/v0.8.1...v0.8.2) (2026-06-05)


### Bug Fixes

* **command:** register /bal alias to override EssentialsX ([d9a0318](https://github.com/TServerNetwork/T-Nexus/commit/d9a031805f2ec76244078b9fdd188e234bc83818))
* **i18n:** resolve message keys not loading from ja_JP.yml ([64d9e64](https://github.com/TServerNetwork/T-Nexus/commit/64d9e6453c8553bca686e5c0c281a4b945405cf8))
* **shop:** remove chest requirement at sign placement, guide player to link ([199bade](https://github.com/TServerNetwork/T-Nexus/commit/199bade6a01e96cfdbfaa95cf0d1664150c6f416))

## [0.8.1](https://github.com/TServerNetwork/T-Nexus/compare/v0.8.0...v0.8.1) (2026-06-05)


### Bug Fixes

* update paper-plugin.yml dependencies to new Paper plugin format ([7781ea8](https://github.com/TServerNetwork/T-Nexus/commit/7781ea8cf6ac7f25f3b4643ab6e3619e18838f7d))

# [0.8.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.7.0...v0.8.0) (2026-06-05)


### Features

* **gui:** add audit history viewer ([#30](https://github.com/TServerNetwork/T-Nexus/issues/30)) ([d0ebaea](https://github.com/TServerNetwork/T-Nexus/commit/d0ebaea9e76ef0028193654a66cc8d8c7c8ab46e))

# [0.7.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.6.0...v0.7.0) (2026-06-05)


### Features

* **economy:** implement player shop system ([#29](https://github.com/TServerNetwork/T-Nexus/issues/29)) ([3bf5842](https://github.com/TServerNetwork/T-Nexus/commit/3bf58420d27150c4afed357875f2c9d10c3929d7))
* **economy:** implement server shop logic ([#28](https://github.com/TServerNetwork/T-Nexus/issues/28)) ([cfa2cea](https://github.com/TServerNetwork/T-Nexus/commit/cfa2ceafbef5b64474938a6e2e3ff2150db3c7da))

# [0.6.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.5.0...v0.6.0) (2026-06-05)


### Features

* **economy:** implement sign shop foundation ([#27](https://github.com/TServerNetwork/T-Nexus/issues/27)) ([270c5cd](https://github.com/TServerNetwork/T-Nexus/commit/270c5cd13896ea9d420376acf8b75d366f5569f0))

# [0.5.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.4.0...v0.5.0) (2026-06-05)


### Features

* **economy:** implement balance and pay flow ([#26](https://github.com/TServerNetwork/T-Nexus/issues/26)) ([22d4430](https://github.com/TServerNetwork/T-Nexus/commit/22d4430a6b3dd2247af7112771ca0d40570c50c6))

# [0.4.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.3.0...v0.4.0) (2026-06-05)


### Features

* **gui:** add anvil gui input manager ([#25](https://github.com/TServerNetwork/T-Nexus/issues/25)) ([d685325](https://github.com/TServerNetwork/T-Nexus/commit/d6853256818a6a6cbf1dabb98aba091959df0a04))

# [0.3.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.2.0...v0.3.0) (2026-06-05)


### Features

* **db:** add economy schema migration ([#24](https://github.com/TServerNetwork/T-Nexus/issues/24)) ([ff87f6a](https://github.com/TServerNetwork/T-Nexus/commit/ff87f6a2524d5351d6f0f79daf85bd121795ec35))

# [0.2.0](https://github.com/TServerNetwork/T-Nexus/compare/v0.1.5...v0.2.0) (2026-06-05)


### Features

* **economy:** implement Vault-backed economy manager ([#23](https://github.com/TServerNetwork/T-Nexus/issues/23)) ([ec067d8](https://github.com/TServerNetwork/T-Nexus/commit/ec067d8cceb5b124b30f45445966c2db20ba0ec8))

## [0.1.5](https://github.com/TServerNetwork/T-Nexus/compare/v0.1.4...v0.1.5) (2026-06-04)


### Bug Fixes

* **ci:** clean before shadowJar in semantic-release prepare to ensure correct version ([0ce716f](https://github.com/TServerNetwork/T-Nexus/commit/0ce716f027bfd5aae7d8467d9c49f6c879df95f7))

## [0.1.4](https://github.com/TServerNetwork/T-Nexus/compare/v0.1.3...v0.1.4) (2026-06-04)


### Bug Fixes

* **ci:** remove duplicate jar upload and show filename in release ([9c6ae98](https://github.com/TServerNetwork/T-Nexus/commit/9c6ae98309c14d32bd8c7b43ebf14cf6d97d754d))

## [0.1.3](https://github.com/TServerNetwork/T-Nexus/compare/v0.1.2...v0.1.3) (2026-06-04)


### Bug Fixes

* **ci:** build jar with correct version in semantic-release prepare phase ([1f3c233](https://github.com/TServerNetwork/T-Nexus/commit/1f3c23396b0f1ad62c91336b2c7125b0efc0b19e))

## [0.1.2](https://github.com/TServerNetwork/T-Nexus/compare/v0.1.1...v0.1.2) (2026-06-04)


### Bug Fixes

* **ci:** attach jar artifact to semantic-release GitHub Release ([6c9d430](https://github.com/TServerNetwork/T-Nexus/commit/6c9d430f2924d6cf762574a35fbba1e22a0aeb83))

## [0.1.1](https://github.com/TServerNetwork/T-Nexus/compare/v0.1.0...v0.1.1) (2026-06-04)


### Bug Fixes

* **command:** suppress permission message on tab completion ([#5](https://github.com/TServerNetwork/T-Nexus/issues/5)) ([e841c2d](https://github.com/TServerNetwork/T-Nexus/commit/e841c2d29fd739c53a2b19c6eb4ffe66225d7f95))
* **gui:** use modern Paper profile API for PLAYER_HEAD ([#4](https://github.com/TServerNetwork/T-Nexus/issues/4)) ([06c8f1d](https://github.com/TServerNetwork/T-Nexus/commit/06c8f1d06e62ca8fa0a9b13d96b4f63cdc748a23))

# Changelog
