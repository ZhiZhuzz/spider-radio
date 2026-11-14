# 第三方材料与素材来源

## 字体

`app/src/main/assets/font.otf` 的内嵌名称为 Noto Sans CJK SC Regular，内嵌版权为 © 2014–2021 Adobe。保留原文件及 SIL OFL 1.1 许可，不将字体改授 GPL。文件 SHA-256：`49ef78f91118a58185547914d3f67c32d9b519895479940e3d7ed174814e3e88`。该文件的原始获取和子集加工记录尚未归档。

- [上游项目](https://github.com/notofonts/noto-cjk)
- [上游字体许可](https://github.com/notofonts/noto-cjk/blob/main/Sans/LICENSE)
- [随附许可](FONT-LICENSE.txt)

## 图片与音频

维护者于 2026-11-14 确认：`city-v2.png`、`snow-v2.png`、`train-v2.png`、`rain-v2.png` 和内置音乐 `demo.wav` 均使用 GPT 生成。项目中的 `tools/audio.py`、`tools/soundscapes.py` 是合成音乐及环境音的过程材料。此来源说明不宣称生成结果必然具有独占版权。

## 网络电台

`radio-stations.json` 与 `radio-data.js` 保留 v0.8.3 的站点目录及来源主页。仓库不包含节目音频，不代理或转播流媒体，也未因开源取得电台内容授权。流地址可能失效；部署、分发或商业使用前应自行核实来源平台的要求。GPL 仅涉及本项目相应材料，不授予电台节目、商标或第三方服务的权利。

## 构建与测试依赖

- Android SDK / JDK：构建环境，不随仓库分发。
- [jsdom](https://github.com/jsdom/jsdom)：MIT，测试依赖。
- [Playwright](https://github.com/microsoft/playwright)：Apache-2.0，测试依赖。
- [JSON-java](https://github.com/stleary/JSON-java)：使用上游 20240303 测试依赖及其许可，不随仓库分发。
- NumPy、SciPy、FFmpeg：仅在重新生成音频时使用，不随源码仓库分发；各组件使用各自许可。

依赖版本与完整性由 `package-lock.json` 记录；下载后的各包仍应保留其自身许可。
