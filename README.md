# 安规刷题（变电检修 · 安全规程题库）

> **当前仓库为私密（private）。** GitHub Free 计划不支持私有仓库的 GitHub Pages，因此文中 `youyoudezhuzhu.github.io/angui-quiz/` 在线地址当前不可用；网页可在本地运行，APK / 手册 / 导入包仍可从本仓库文件或 Releases（需登录有权限的账号）获取。

一套题库，四种用法：**网页刷题、安卓通知栏搜题、快速记忆手册、Excel 导入包**。

- **在线入口**：<https://youyoudezhuzhu.github.io/angui-quiz/>
- **网页刷题**：<https://youyoudezhuzhu.github.io/angui-quiz/web/>
- **安卓 APK**：<https://youyoudezhuzhu.github.io/angui-quiz/download/quiz-search-1.0.2.apk>
- **速记手册**：<https://youyoudezhuzhu.github.io/angui-quiz/handbook/安规速记手册.pdf>

> 原 `substation-quiz`、`quiz-search-android` 两个仓库已合并到本仓库，以后只维护这一个项目。

## 题库

| 项目 | 数量 |
| --- | --- |
| 唯一题 | **119**（单选 103 · 读图 14 · 问答 2） |
| 源文件原始条目 | 125（xlsx 118 + xls 5 + doc 2） |
| 完全重复 | 6（xlsx 内部 1 + xls→xlsx 跨文件 5） |

- 6 条重复：xlsx 第 96/98 行是同一题；xls 的 5 题分别等于 xlsx 第 62/67/68/72/74 行，均逐字一致（含解析）。
- 14 道读图题的原题配图缺失（源文件未附带图片）。
- 2 道问答题导入 Excel 格式不支持（答案只支持 A-G），单独提供参考答案。

核对材料：[125 条逐条核对表](docs/题库核对表.xlsx) · [核对说明](docs/核对说明.md) · [数据独立校验](docs/数据独立校验.md)。

## 目录结构

```
index.html            项目首页（GitHub Pages 入口）
web/                  网页刷题（纯静态）
  index.html
  css/ js/            样式 + 题库数据 + 逻辑
  dist/               离线单文件版
  docs/               截图
android/              安卓通知栏搜题 App（Java，无 Gradle）
  build.sh            一键构建 APK
  src/ res/ assets/   App 源码与题库
  dist/               构建好的 APK
handbook/             快速记忆手册（PDF / 速记卡 PNG / HTML / Markdown）
import/               题库导入包（Excel / TXT / 问答题参考答案）
data/questions.json   唯一题库数据（119 题）
download/             APK 下载（供 Pages 直接下载）
docs/                 核对表、核对说明、数据校验报告
tools/sync-data.py    从 data/questions.json 同步网页和 App 的题库
```

## 修改题库

1. 只改 `data/questions.json`；
2. 运行同步脚本：

```bash
python3 tools/sync-data.py
```

会更新 `web/js/questions.js` 和 `android/assets/questions.json`（网页与 App 共用同一份数据）。
导入包和手册是预生成文件，题库变动后需要重新生成。

## 构建安卓 APK

```bash
cd android
ANDROID_HOME=/opt/android-sdk ./build.sh
# 产物：build/quiz-search-1.0.2.apk
```

`minSdk 26`（Android 8.0+），`targetSdk 33`；签名脚本会在 `android/build/` 生成自签名密钥（不入库）。
当前 APK SHA-256：`00f964191437db53bebbc50fc1e7bd43a3ca0c371172b3ca4837ad3df6309158`（`android/dist/` 与 `download/` 中的已发布版本）。

> 注意：`./build.sh` 在缺少 `android/build/quiz-search.keystore` 时会生成新的自签名证书，重新构建出的 APK 签名会与已发布版本不同（不能直接覆盖安装）。如需保持同一签名，请复用原 keystore（未随仓库公开分发）。

## 本地运行网页

```bash
python3 -m http.server 8080
# 打开 http://127.0.0.1:8080/         项目首页
# 打开 http://127.0.0.1:8080/web/     在线刷题
```

## 说明

- 题库来源：变电检修（B）· 读图 5 选 2 · 问答题 2 选 1。
- 网页和 App 都是纯本地、离线可用；App 不申请网络权限。
- 本仓库内容仅作学习记忆辅助，正式考试以《变电安规》原文为准。
