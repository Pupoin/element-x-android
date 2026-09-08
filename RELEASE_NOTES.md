# 🚀 Element X+ (v26.09.08)

---

## 🇬🇧 English: Release Notes (v26.09.08)

Element X+ **v26.09.08** is a major feature update following `v26.09.1-latex`. It introduces an architectural two-level AST message parser, full TextMate grammar syntax highlighting, interactive code cards with full-screen viewers, and deep UI/UX gesture improvements across messages.

### 🌟 What's New Since `v26.09.1-latex`

#### 1. 🏗️ Two-Level AST Message Pipeline (`MessageBlock` & `InlineNode`)
- **Robust Layout Engine**: Completely re-engineered rich message rendering from legacy regex-based parsing to an AST-driven pipeline (`HtmlToMessageAstParser` & `MarkdownToMessageAstParser`).
- **Clean Block/Inline Separation**: Block elements (Paragraph, Heading, CodeBlock, LatexBlock, Table, Quote, List, Rule) and inline elements (Text, Inline Math, Code, Links, Bold, Italic) render hierarchically without layout breakage or recursion crashes.

#### 2. 🎨 TextMate Syntax Highlighter Engine (VS Code / TM4E Parity)
- **True Headless Tokenizer**: Integrated the industry-standard Eclipse TM4E engine via Sora Editor, parsing code lines with multi-line grammar state tracking (`ruleStack`).
- **Comprehensive Language Support**: Highlighting for Kotlin, Java, Python, Rust, JavaScript, TypeScript, JSON, Shell/Bash, HTML/XML, and C/C++.
- **Intelligent Detection**: Auto-infers languages using Shebang markers (`#!/bin/bash`, `#!/usr/bin/env python`), command patterns (`sudo`, `curl`, `git`), and structured data formats when language tags are absent.

#### 3. 💻 Interactive Code Blocks & Fullscreen Viewer
- **Collapsible Code Cards**: Long code snippets in timeline bubbles automatically truncate to 8 lines with a clean "Expand all" action bar.
- **Strict Line Number & Copy Isolation**: Left gutter displays line numbers enclosed in `DisableSelection`. Copying via button or long-press selection **never includes line numbers**.
- **Fullscreen Viewer (`CodeFullscreenViewer`)**:
  - **Pinch-to-Zoom**: Two-finger zoom smoothly rescales font size from 75% to 250% with strict line-height parity between line numbers and code text.
  - **Full-Screen Free Horizontal Scrolling**: Resolved touch interception so horizontal panning works across the entire screen (including bottom blank areas) with natural swipe direction (swipe left reveals right content).
  - **Zero-Bounce Fling**: Fixed double-scrollable fling competition, ensuring natural deceleration without spring-back bounce.

#### 4. 🔍 Fullscreen Viewers for Formulas & Tables
- **Fullscreen LaTeX Viewer (`LatexFullscreenViewer`)**: Tap block formulas to launch an edge-to-edge dialog with two-dimensional panning and two-finger pinch-to-zoom.
- **Fullscreen Table Viewer (`TableFullscreenViewer`)**: Complex, wide Markdown and HTML tables can be inspected in full-screen with smooth multi-touch navigation.

#### 5. 💬 Smooth Interaction & Context Menu Fixes
- **Universal Bubble Long-Press**: Tuned gesture hit testing across messages so long-pressing anywhere (text, code borders, formula cards, or blank space) reliably summons the message action sheet.
- **Rich Message Quotes & Lists**: Beautiful native rendering for nested block quotes and bulleted/numbered lists.

---

## 🇨🇳 中文：更新说明 (v26.09.08)

Element X+ **v26.09.08** 是继 `v26.09.1-latex` 之后的重大功能更新版本。本次升级带来了统一的双层 AST 消息解析架构、工业级 TextMate 语法高亮引擎、支持行号隔离与自由缩放的全屏代码查看器，以及全方位的消息交互手势优化。

### 🌟 相比上次 Tag (`v26.09.1-latex`) 的更新内容

#### 1. 🏗️ 全新双层 AST 消息解析与原生渲染架构
- **彻底告别正则拼接**：构建了基于 AST 的两层消息处理管线（`HtmlToMessageAstParser` 与 `MarkdownToMessageAstParser`），消除了复杂消息混排时的排版混乱与崩溃隐患。
- **块级与行内分层解耦**：块级节点（段落、标题、代码块、数学公式、表格、引用块、列表、分割线）与行内节点（纯文本、行内公式、行内代码、超链接、加粗、斜体等）统一组织，层次清晰。

#### 2. 🎨 TextMate 语法高亮引擎（对齐 VS Code 代码着色）
- **无 UI 侵入的 Headless 分词引擎**：基于 Eclipse TM4E / Sora Editor 核心，逐行分析语法并保存词法栈状态（`ruleStack`），精准映射到 Compose `AnnotatedString` 的 `SpanStyle`。
- **广泛的语言支持**：原生支持 Kotlin、Java、Python、Rust、JavaScript、TypeScript、JSON、Shell/Bash、HTML/XML、C/C++ 等主流语言。
- **智能语言自动推断**：自动识别 Shebang（`#!/bin/bash` 等）、常见命令行操作（`sudo`、`curl`、`git` 等）以及 JSON 结构，无语言标注也能拥有高亮。

#### 3. 💻 交互式代码卡片与全屏沉浸式代码查看器
- **智能折叠**：时间线气泡中超过 8 行的长代码自动截断，提供优雅的“展开全部”操作栏。
- **行号与选区严格隔离**：左侧行号栏由 `DisableSelection` 严格包裹，**无论是点击“复制”按钮还是长按光标手柄框选复制，复制内容绝对不含行号**。
- **全屏代码查看器 (`CodeFullscreenViewer`) 深度优化**：
  - **自由缩放**：支持双指捏合缩放（75%~250%），行号与代码行高始终保持 1:1 精确对齐。
  - **全域横向平滑滚动**：解决代码只有几行时屏幕下方无法左右滑动的问题。屏幕底部全域均可自然横向滚动，手势方向符合自然直觉（手向左滑展现右侧内容）。
  - **惯性滑动永不回弹**：彻底消除多层嵌套滚动带来的 Fling 冲突，快速滑动松手后平滑自然减速停止，绝不反弹。

#### 4. 🔍 公式与表格沉浸式全屏查看器
- **全屏公式查看器 (`LatexFullscreenViewer`)**：轻触公式卡片即可进入全屏漫游模式，支持双指自由缩放与二维拖拽，复杂公式、长矩阵一览无余。
- **全屏表格查看器 (`TableFullscreenViewer`)**：大型宽表格支持全屏多点触控与漫游查看。

#### 5. 💬 消息手势与富文本细节调优
- **全域长按菜单修复**：优化手势分发与命中测试，长按普通文本、代码卡片、公式或气泡空白处的任何位置均能稳定弹出操作气泡与菜单。
- **引用块与列表增强**：原生渲染多层 Markdown 块引用（Quote）与有序/无序列表，对齐现代排版体验。

---

### 📦 安装包下载指南 / Download APKs

- **arm64-v8a** (主流 64 位 Android 手机推荐): `app-fdroid-arm64-v8a-release.apk`
- **armeabi-v7a** (32 位老旧设备): `app-fdroid-armeabi-v7a-release.apk`
- **Universal** (全架构通用包): `app-fdroid-universal-release.apk`
