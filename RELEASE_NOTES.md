# 🚀 Element X+ (LaTeX Formula Rendering Edition)
# 🚀 Element X+ (LaTeX 数学公式增强版)

---

## 🇬🇧 English

Element X+ is an enhanced version of Element X Android featuring a native, high-performance LaTeX math rendering engine, tailored for academic communication, technical discussions, and scientific collaboration.

### ✨ Key Features

1. **📐 Native LaTeX Math Rendering**
   - **Inline Math**: Seamlessly renders `$E=mc^2$`, `$\nabla \cdot \mathbf{B} = 0$` within message bubbles while maintaining natural typographic baseline alignment.
   - **Block Math**: Displays `$$ ... $$` formulas in standalone, horizontally-scrollable cards. Complex equations, fractions, and large matrices remain crisp without down-scaling, line wrapping, or clipping.
   - **Matrix MSC2191 Compliant**: Fully compatible with the Matrix MSC2191 math standard used by Element Web and Element Desktop.

2. **📊 Native Markdown & HTML Table Rendering**
   - Seamlessly converts HTML `<table>` and Markdown tables into native Compose cards.
   - Distinct header background, subtle zebra row striping, rounded borders, and smooth horizontal scrolling for wide tables.
   - Tap the table to quickly copy raw Markdown source text with an instant toast notification.

3. **🔤 Enhanced Heading Typography (Element Web Parity)**
   - Headers (`# H1` to `###### H6`, and HTML `<h1>` to `<h6>`) now render with distinct proportional font sizes and bold weights, matching Element Web's presentation.

4. **🧠 Broad Environment Compatibility**
   - Built-in automatic normalization for popular LaTeX environments including `align*` / `align`, `gather*` / `gather`, `equation*` / `equation`, `matrix` / `pmatrix`, and `aligned`.
   - Tolerates extra whitespace and formatting variations gracefully.

5. **📋 Smooth Interaction & Fast Copy**
   - **Single-tap formula or table card**: Copies the raw LaTeX / Markdown code directly to the clipboard with an instant toast notification.
   - **Long-press message**: Opens the standard context menu with a "Select text" sheet for precise text selection and copying.

6. **⚙️ Toggle in Advanced Settings**
   - Easily enable or disable formula rendering anytime under **Settings → Advanced Settings → Render mathematical formulas**.
   - When disabled, messages fallback to raw LaTeX source text without custom rendering.

7. **📱 Side-by-Side Coexistence**
   - Packaged as `io.element.android.x.custom` and labeled as **Element X+**.
   - Can be installed and run alongside the official Element X release without signature conflicts or overwrites.

### 📦 Installation Guide
- Most users (64-bit ARM devices): Download `app-fdroid-arm64-v8a-release.apk`.
- Older 32-bit devices: Download `app-fdroid-armeabi-v7a-release.apk`.

---

## 🇨🇳 中文说明

Element X+ 是基于 Element X Android 开发的增强版本，内置专业的 LaTeX 数学公式渲染引擎与 Markdown 增强排版，专为学术交流、技术探讨和专业知识展示优化。

### ✨ 核心特性

1. **📐 原生 LaTeX 数学公式渲染**
   - **行内公式**：支持 `$E=mc^2$`、`$f(x) = \frac{1}{\sqrt{2\pi}} e^{-\frac{x^2}{2}}$`，平滑嵌入消息富文本，保持自然阅读基线。
   - **块级公式（方案 B）**：支持 `$$ ... $$` 独立卡片渲染。内置横向平滑滚动条，长公式、大矩阵不缩小、不截断、不换行，保持原始字体清晰度。

2. **📊 原生 Markdown / HTML 表格卡片渲染**
   - 解决官方客户端解析表格文字挤成一团的问题，自动解析 HTML `<table>` 与 Markdown 表格为原生 Compose 表格卡片。
   - 区分表头底色、行间斑马纹隔行变色、圆角边框，宽表格支持横向平滑滚动。
   - 轻触表格卡片即可一键复制原始 Markdown 格式文本。

3. **🔤 标题字号层级优化（对齐 Web 版排版体验）**
   - 全面支持 HTML `<h1>`~`<h6>` 与 Markdown 语法标题（`#` 至 `######`），自动应用等比放大的标题字号与粗体层级，大标题更醒目，结构一目了然。

4. **🧠 全面兼容主流 LaTeX 环境**
   - 智能兼容 `align*` / `align`、`gather*` / `gather`、`equation*` / `equation`, `matrix` / `pmatrix` 等所有常用数学环境。
   - 自动消除常见渲染报错，支持大括号多余空格容错。

5. **📋 便捷的公式与表格复制交互**
   - **单击公式 / 表格卡片**：一键将完整的 LaTeX / Markdown 源码复制到剪贴板，并弹出快捷提示。
   - **长按消息卡片**：调出消息操作菜单，支持“选择文本”进行源码划选复制。

6. **⚙️ 高级设置开关**
   - 在 **设置 → 高级设置 → 渲染数学公式** 中随时可开启或关闭渲染。
   - 关闭后即刻回退为标准纯文本展示，满足不同场景需要。

7. **📱 独立共存安装**
   - 应用包名设为 `io.element.android.x.custom`，应用名称显示为 **Element X+**。
   - 可与官方 Element X 同时安装在同一部手机上，互不覆盖、互不影响。

---

### 📦 安装说明
- 推荐下载 `app-fdroid-arm64-v8a-release.apk`（主流 64 位 Android 手机）。
- 若需要 32 位老机型支持，请下载 `app-fdroid-armeabi-v7a-release.apk`。
