# GitLab+ Merge Tool

IntelliJ IDEA 插件，在 IDE 内完成 GitLab 合并请求的列表浏览、详情查看、评审、Diff 审查、流水线查看与合并请求创建。

> English documentation: [README.en.md](README.en.md)

| 项目 | 值 |
| --- | --- |
| 版本 | 0.3.0 |
| 插件 ID | `com.hakil.gitlabplus` |
| 目标 IDE | IntelliJ IDEA 2026.2 及以上（`since-build=262`） |
| 实现语言 | Java 25，`javac` 直接构建 |
| 依赖模块 | `com.intellij.modules.platform`、`Git4Idea` |
| 自测 | 25 套，可离线运行 |

## 功能

### 合并请求列表

- 状态过滤：全部 / 待合并 / 已合并 / 已关闭
- 游标分页，每页 50 条，「加载更多」追加
- 搜索匹配编号、标题、源分支、目标分支
- 列表与详情为垂直分栏，初始比例 62:38；未选中行时详情隐藏
- 头部操作为「刷新」「新建 MR」「自检」，搜索框位于列表上方

### 详情与操作

页签：概览、描述、讨论、变更、CI。

概览字段：状态、源/目标分支、作者、指派、审阅人、已批准、标签、流水线、可合并、冲突、草稿、压缩策略、创建时间、更新时间、当前账号权限。描述按 Markdown 渲染。

操作：合并、压缩合并、批准、撤回批准、关闭、重新打开、标记草稿/就绪、变基、设置审阅人、打开网页、刷新详情。

合并对话框字段：提交信息、压缩提交、合并后删除源分支。

设置审阅人从项目成员列表多选，支持按用户名或姓名过滤。

### 评审

- 讨论列表默认只含人工评论，`system=true` 的条目默认隐藏，可切换显示
- 回复选中讨论；发表新评论；两者均通过对话框输入
- Resolve / Unresolve：`PUT /merge_requests/:iid/discussions/:did`，query 参数 `resolved=true|false`
- 点赞切换：对讨论中第一条非系统评论执行 reaction 增删

### 变更

- 逐文件 unified diff，双列行号，新增行绿底、删除行红底
- 选中行可发评论或生成 `suggestion` 建议块
- 已读标记以 `headSha` 为版本，`headSha` 变化后失效；已读文件显示 `✓` 并置灰，可隐藏
- 单文件渲染上限 3000 行，超出部分不渲染并提示
- 「在 IDEA Diff 中打开」：读取 base / head 两侧全文，交给 IDE 原生两栏对比

### 流水线与 Job 日志

- 列出该 MR 关联的全部流水线，上下分栏；选中流水线即显示其 job 列表
- 列：`#`、状态、触发、来源、SHA、创建时间。触发列使用向右箭头（`feature/x → dev`）；来源列为 `合并` / `MR 校验` / `推送` / `目标分支` / `定时`，无法判定时显示 GitLab 原始 `source`
- Job 操作：运行（`manual`）、重跑（`failed` / `canceled` / `success`）、取消（`running` / `pending`）
- 流水线操作：重跑、取消、打开网页
- 关注流水线：按 pipeline id 每 10 秒轮询，状态变化回传界面，结束时按结果分级通知；默认超时 30 分钟，可在设置中修改
- Job 日志：按行着色（错误红、警告橙、成功绿、分组灰、命令蓝），移除 ANSI 转义与 `\r`，Ctrl+滚轮调整字号，跳至末尾，复制全文，运行中可开启自动刷新，仅渲染尾部 4000 行

### 新建合并请求

- 源/目标分支为可编辑下拉，标注 `[本地·已推送]` / `[本地·未推送]` / `[仅远程]`
- 源分支默认为当前分支
- 目标分支按源分支前缀排序：`feature/*`、`bugfix/*` → `dev`、`develop`、`development`；`hotfix/*`、`fix/*` → `master`、`main`、`prod`、`production`；`release/*` → `master`、`main`
- 标题默认 `Merge branch '<source>' into '<target>'`；标题与描述模板可配置
- 本地领先远端时自动执行 `git push -u origin <branch>`；不执行强制推送
- 推送被拒时中止创建，提示先执行 Pull

### 合并后本地动作

- 执行 `git fetch --all --prune`
- 可选：切换到目标分支（默认关闭）
- 可选：删除已合并的本地源分支（默认关闭）
- 各步骤独立执行，失败不影响合并结果

### 界面语言

取值：跟随系统（默认）、中文、English。修改后需重启 IDE。资源束位置：`src/main/resources/messages/`。

## 环境要求

- IntelliJ IDEA 2026.2+
- JDK 25（编译用）
- Gradle 由项目自带的 wrapper 提供，无需预装
- GitLab Personal Access Token，scope 包含 `api`

## 构建

```powershell
.\gradlew.bat buildPlugin
```

产物：`build\distributions\GitLabPlus-<version>.zip`，内含 `GitLabPlus/lib/GitLabPlus-<version>.jar`。

平台依赖由 `gradle.properties` 的 `platformLocalPath` 指向本机 IDEA 安装目录：

```properties
platformLocalPath=D:/IDEA/2026.2
```

可用 `-PplatformLocalPath=...` 或 `~/.gradle/gradle.properties` 覆盖。若要改为下载发行包，在 `build.gradle.kts` 中把 `local(...)` 换成 `intellijIdea("2026.2")`。

版本号单一来源：`gradle.properties` 的 `pluginVersion`，同时决定 jar 名、zip 名与 `plugin.xml` 的 `<version>`。

其他常用任务：

```powershell
.\gradlew.bat compileJava        # 仅编译
.\gradlew.bat buildPlugin        # 产出插件 zip
.\gradlew.bat test               # 运行全部自测
.\gradlew.bat verifyPlugin       # 二进制兼容性校验（需下载校验器）
```

## 安装与升级

```powershell
# IDEA 已关闭
powershell -ExecutionPolicy Bypass -File scripts\install.ps1

# IDEA 仍在运行时等待其退出后再安装，最长等待 7200 秒
powershell -ExecutionPolicy Bypass -File scripts\install.ps1 -WaitSeconds 7200
```

安装目录：`%APPDATA%\JetBrains\IntelliJIdea*\plugins\GitLabPlus\`。脚本取 `build\distributions` 中最新的 zip，解压后覆盖同名目录。

卸载：删除上述 `GitLabPlus` 目录，或在 `Settings → Plugins → Installed` 中卸载。

IDEA 运行期间插件 jar 被内存映射占用，无法覆盖。

## 自测

```powershell
.\gradlew.bat test
```

25 个测试类，JUnit 5。一个测试类内的多条断言全部执行后统一上报，因此一次可看到该类中所有不通过的条目。

覆盖对象：Diff 解析与 position 构造、讨论解析、流水线状态与角色、分支排序与推送判定（含真实 git 仓库构造的非快进拒绝场景）、模板渲染、Markdown 渲染、ANSI 清理、日志截断与配色、已读索引淘汰、远端 URL 解析、能力裁剪、资源束键集一致性与 Locale 精确匹配。

`gradlew check` 额外执行三项校验：`verifyPluginProjectConfiguration`（工程配置与依赖声明一致性）、`verifyPluginStructure`（插件 zip 结构与描述符）、`verifyPlugin`（二进制兼容性）。

## 配置项

`Settings → Tools → GitLab+ Merge Tool`

| 配置项 | 取值与说明 |
| --- | --- |
| GitLab 服务器 | 服务器地址，同时作为凭据键 |
| Access Token | 存于 IDE 凭据库，不写入配置文件；支持从内置插件导入、单独清除 |
| 项目全路径 | `group/sub/project`；为空时从 git remote 推导 |
| 项目下拉 | 左列为扫描到的远程项目，右列为下拉内容；支持左右移动与上下排序；右侧为空时显示全部 |
| 合并后收尾 | 切换到目标分支、删除已合并的本地源分支，默认均关闭 |
| 新建 MR 模板 | 标题、描述模板，支持 `${source}` `${target}` `${branchTitle}` `${project}`；留空使用内置默认 |
| 列表默认状态 | `all` / `opened` / `merged` / `closed` |
| 新建 MR 默认勾选 | 删除源分支、压缩提交 |
| 流水线关注超时 | 分钟，默认 30 |
| 界面语言 | 跟随系统 / 中文 / English |

配置文件：

| 文件 | 内容 |
| --- | --- |
| `options\gitlab-plus.xml` | 服务器、项目、模板、默认值、项目下拉顺序 |
| `options\gitlab-plus-language.xml` | 界面语言 |

Token 存储于 IDE 凭据库，不在上述文件中。

## 目录结构

```
build.gradle.kts             构建脚本（IntelliJ Platform Gradle Plugin 2.x）
settings.gradle.kts          工程名
gradle.properties            版本号、平台路径、编译参数
gradle/wrapper/              Gradle wrapper
src/main/java/com/hakil/gitlabplus/
  api/        GraphQL 查询、能力探测、REST 路径与客户端、JSON 解析
  git/        仓库与远端解析、分支列举与推送、合并后动作、进程执行封装
  i18n/       资源束入口、界面语言服务
  model/      数据记录与纯函数
  service/    MergeRequestApi、MergeRequestActions、PipelineApi、GitLabService、
              评审与 Diff 服务、自检、流水线关注
  settings/   配置持久化、Token 存取、内置插件 Token 导入、设置变更广播
  storage/    已读标记存储
  ui/         工具窗口、详情面板、对话框、表格模型、渲染器
src/main/resources/
  META-INF/plugin.xml
  messages/GitLabPlusBundle.properties
  messages/GitLabPlusBundle_zh_CN.properties
src/test/java/test/          25 个 JUnit 5 测试类
docs/api-contracts.md        已实现端点与参数规则
scripts/install.ps1          安装（支持 -WaitSeconds）
```

## 限制

- 认证方式仅 Personal Access Token，单服务器、单账号；不支持 OAuth
- 不提供分支检出，使用 IDE 自带 Git
- Diff 通过单次 `/changes` 请求获取，无分页；单文件渲染上限 3000 行
- 无 update site，升级需重新安装
- 流水线列表最多展示 30 条
- Job 日志仅渲染尾部 4000 行
- 已读标记上限 500 条，超出按写入时间淘汰

## 与内置 GitLab 插件

- 两者可同时启用，互不修改对方的文件与配置
- 「从内置插件导入 Token」读取 `options\gitlab.xml` 的账户列表，使用 `CredentialAttributes("GitLab", accountId)` 通过平台 `PasswordSafe` 读取凭据
- 内置插件使用 OAuth 登录时，导入的 token 会过期

## 版本

| 版本 | 内容 |
| --- | --- |
| 0.2.0 | 服务层按资源拆分；HTTP 与 git 调用超时；流水线关注使用独立调度器；审阅人改为成员多选；讨论列表过滤系统条目；Diff 支持 `diff_refs` 回退与 IDE 原生对比；界面文案国际化与语言切换 |
| 0.1.0 | 列表、详情、操作、评审、Diff 与行内评论、合并后刷新本地 Git |
