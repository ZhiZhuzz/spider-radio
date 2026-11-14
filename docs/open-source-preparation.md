# Android v0.8.3 开源整理记录

整理日期：2026-11-14。源码基线：本地保存的 `SpiderRadio-0.8.3-source.zip`。这里的版本号来自原始 Android 配置，不据此声称它是维护者所有设备上的最新版本。

## 本次检查

- 47 个 `app/` 内文件与原始源码逐字节一致，未修改应用功能、图像、音频或字体。
- 本机重新编译并运行 8 项 Java 逻辑测试、2 项生产方法模拟、3 项 DOM 回归、6 项浏览器回归：**19 通过，0 失败，0 跳过**。
- 使用 JDK 17、Node.js、jsdom 26.1.0、Playwright 1.62.1 和已有 Microsoft Edge；浏览器测试不等于 Android WebView 或真机验收。
- 按文件名和常见密钥特征扫描待提交文件，未发现签名私钥、GitHub / OpenAI 令牌等已识别模式。这不构成对未知格式秘密的绝对保证。
- 此次未重新运行完整 Android SDK 构建，也未进行 Android 真机或模拟器验收。原交付的恢复构建记录单独标为历史证据。

## 整理改动

- 加入维护者选择的 GPL-3.0-only 许可、NOTICE、素材来源和第三方许可说明。
- 重新编写 README，补充真实功能、AI 协作、构建步骤、架构和验证边界。
- 修复测试在 Windows 上使用本地文件 URL 的方式，移除旧机器的字体绝对路径；明确 Python 和 Java 测试的 UTF-8 编码。没有放宽断言或减少现有测试。
- 增加忽略规则和文本换行规则；不提交签名材料、依赖、构建缓存、历史机器日志和旧差异补丁。
- 保留三个 v0.8.3 浏览器界面截图用于预览，并明确它们使用演示数据。
- 原工作流作为 `docs/ci/android-tests.yml` 示例保存，未启用远程自动执行。

## 许可与来源

维护者于 2026-11-14 选择 GPL-3.0，并确认四张背景图和内置音乐使用 GPT 生成。代码和设计存在大量 GPT 协助，不描述为维护者完全独立编写。字体保留 SIL OFL 1.1；网络电台节目及第三方服务的权利不随本项目 GPL 许可授予。详见根目录 NOTICE 与 THIRD_PARTY_NOTICES.md。

GPL 标准文本取自 [SPDX 许可列表](https://github.com/spdx/license-list-data/blob/main/text/GPL-3.0-only.txt)，字体上游许可见 [Noto CJK](https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE)。
