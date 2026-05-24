# MSFriends (MSF)

为低版本 Minecraft 添加好友系统的多平台模组，支持 Fabric、NeoForge 和 Forge。

## 功能

- 好友列表管理
- 在线状态显示
- P2P 连接（基于 WebRTC）
- 跨平台支持（Fabric / NeoForge / Forge）
- 支持多 Minecraft 版本

## 支持版本

| Minecraft 版本 | Fabric | Forge | NeoForge | 状态 |
|---------------|--------|-------|----------|------|
| 1.16.5 | ✅ | ❌ | ❌ | ⚠️ 未测试 |
| 1.18.2 | ✅ | ❌ | ❌ | ⚠️ 未测试 |
| 1.19.2 | ✅ | ❌ | ❌ | ⚠️ 未测试 |
| 1.20.1 | ✅ | ❌ | ❌ | ⚠️ 未测试 |
| 1.21.1 | ✅ | ❌ | ❌ | ⚠️ 未测试 |
| 1.21.11 | ✅ | ✅ | ✅ | ⚠️ 未测试 |
| 26.1.2 | ✅ | ✅ | ✅ | 已测试 |

> **注意**: 1.16.5 ~ 1.21.1 版本尚未经过完整测试，如遇到问题请提交 Issue。
> **提醒**: 如果需要半自动化测试可以使用MCP "npx craftmcp"

## 项目结构

```
MSF/
├── common/                          # 共享纯逻辑模块（无 MC 依赖）
├── versions/
│   ├── 1.16.5/                      # MC 1.16.5 适配
│   │   ├── common/                  #   版本特定代码（无加载器依赖）
│   │   └── fabric/                  #   Fabric 入口点
│   ├── 1.18.2/                      # MC 1.18.2 适配
│   │   ├── common/
│   │   └── fabric/
│   ├── 1.19.2/                      # MC 1.19.2 适配
│   │   ├── common/
│   │   └── fabric/
│   ├── 1.20.1/                      # MC 1.20.1 适配
│   │   ├── common/
│   │   └── fabric/
│   ├── 1.21.1/                      # MC 1.21.1 适配
│   │   ├── common/
│   │   └── fabric/
│   ├── 1.21.11/                     # MC 1.21.11 适配
│   │   ├── common/
│   │   ├── fabric/
│   │   ├── forge/                   #   Forge 入口点（含 ShadowJar 重定位）
│   │   └── neoforge/                #   NeoForge 入口点（含 ShadowJar 重定位）
│   └── 26.1.2/                      # MC 26.1.2 (1.21.5) 适配
│       ├── common/
│       ├── fabric/
│       ├── forge/
│       └── neoforge/
└── out/                             # 构建产物输出目录
```

## 构建要求

- Java 17+（不同 MC 版本要求不同 Java 版本）
  - 1.16.5 ~ 1.20.1：Java 17
  - 1.21.x：Java 21
  - 26.1.2：Java 25
- Gradle 8+

## 构建

```bash
./gradlew build
```

构建产物将输出到 `out/` 目录，包含格式：
- `msfriends-fabric-<版本>+<MC版本>.jar`
- `msfriends-forge-<版本>+<MC版本>.jar`
- `msfriends-neoforge-<版本>+<MC版本>.jar`

### 仅构建特定版本

```bash
./gradlew :versions:<MC版本>:fabric:remapJar
# 例如：
./gradlew :versions:1.20.1:fabric:remapJar
./gradlew :versions:1.21.11:neoforge:relocateFatJar
```

## 技术细节

### 跨版本兼容

- 使用 `instanceof` 模式匹配替代 Java 21+ 的 `switch` 模式匹配，以兼容 Java 17
- `YggdrasilFriendsService` 接受预解析的路由 URL，避免直接依赖 `Environment` 类型（该类型在 authlib 3.x 中为 class，在 7.x 中为 interface）
- 1.19.2 使用独立的 authlib 排除列表，避免与 authlib 3.x 运行时冲突
- 26.1.2 使用 Fabric Loom 的 no-remap 模式，因为该版本已完全去混淆

### P2P 连接

基于 WebRTC 的点对点连接，通过信令服务实现 NAT 穿透和 ICE 协商。

## 已知冲突

| 冲突模组 | 状态 |
|---------|------|
| ViaFabricPlus | 等待模组版本更新 |

## 许可证

本项目采用 MPL2 许可证。详见 [LICENSE](LICENSE) 文件。
