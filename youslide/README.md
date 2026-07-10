# YouSlide

让 YouTube 拥有类似 Bilibili 的播放手势。

## 功能

| 手势 | 效果 |
|------|------|
| 左右滑动 | 拖动进度条（快进/快退） |
| 左侧上下滑 | 调节亮度 |
| 右侧上下滑 | 调节音量 |
| 双击 | 暂停 / 播放 |

## 适用版本

- YouTube 21.26.364
- 理论上兼容相近版本

## 如何获取 APK（无需自己编译）

### 方法一：GitHub Actions 自动编译（推荐）

1. 把 `youslide` 整个文件夹上传到你自己新建的 GitHub 仓库
2. 上传后，GitHub 会自动编译
3. 在仓库页面点 **Actions** → 点最新的运行记录 → 拉到最下面下载 `youslide-debug`

如何上传到 GitHub：
- 去 [github.com](https://github.com) 注册账号
- 点右上角 **+** → **New repository**，起个名字比如 `youslide`
- 下载 [GitHub Desktop](https://desktop.github.com)，把 `youslide` 文件夹拖进去提交

### 方法二：Android Studio 本地编译

1. 用 Android Studio 打开 `youslide` 目录
2. 等 Gradle 同步完成
3. 菜单 Build → Build Bundle(s) / APK(s) → Build APK(s)
4. APK 在 `app/build/outputs/apk/debug/app-debug.apk`

## 如何安装使用

1. 安装 APK
2. 打开 LSPosed 管理器 → 模块 → 启用 **YouSlide**
3. 勾选作用域 **YouTube**
4. 强制停止 YouTube，重新打开
5. 播放视频，试试手势

## 需要环境

- 手机已 Root
- 已安装 LSPosed（2.1+）
- YouTube 21.26.364

## 项目结构

```
youslide/
├── app/src/main/java/com/youslide/
│   ├── MainHook.java              # Xposed 入口
│   ├── GestureHandler.java        # 手势识别 & 分发
│   ├── BrightnessController.java  # 亮度调节
│   └── VolumeController.java      # 音量调节
├── .github/workflows/build.yml    # GitHub 自动编译
└── README.md
```
