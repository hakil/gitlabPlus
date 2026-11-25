# GitLab+ Merge Tool

An IntelliJ IDEA plugin for GitLab merge requests: browse the list, read details, review, inspect diffs, follow pipelines and create merge requests, all inside the IDE.

> 中文文档：[README.md](README.md)

| Item | Value |
| --- | --- |
| Version | 0.3.1 |
| Plugin ID | `com.hakil.gitlabplus` |
| Target IDE | IntelliJ IDEA 2026.2+ (`since-build=262`) |
| Language | Java 25, built with plain `javac` |
| Depends on | `com.intellij.modules.platform`, `Git4Idea` |
| Tests | 25 suites, runnable offline |

## Features

### Merge request list

- State filter: All / Opened / Merged / Closed
- Cursor pagination, 50 per page, **Load more** appends
- Search matches IID, title, source branch and target branch
- List and details share a vertical split, initial ratio 62:38; details are hidden while no row is selected
- Header actions: refresh, new MR, self-check; the search field sits above the list

### Details and actions

Tabs: Overview, Description, Discussions, Changes, CI.

Overview fields: state, source/target branch, author, assignees, reviewers, approvals, labels, pipeline, mergeable, conflicts, draft, squash strategy, created and updated time, current-user permissions. Description is rendered as Markdown.

Actions: merge, squash and merge, approve, revoke approval, close, reopen, mark draft/ready, rebase, set reviewers, open in browser, refresh details.

Merge dialog fields: commit message, squash commits, delete source branch.

Reviewers are selected from the project member list (multi-select, filterable by username or name).

### Review

- The discussion list contains human comments only; items with `system=true` are hidden by default and can be shown
- Reply to the selected discussion; post a new comment; both use an input dialog
- Resolve / Unresolve: `PUT /merge_requests/:iid/discussions/:did` with query parameter `resolved=true|false`
- Like toggle: adds or removes a reaction on the first non-system comment of the discussion

### Changes

- Per-file unified diff, dual line numbers, additions on green, deletions on red
- A selected line can be commented or turned into a `suggestion` block
- Viewed marks are versioned by `headSha` and invalidate when it changes; viewed files show `✓`, are greyed out and can be hidden
- Rendering is capped at 3000 lines per file; the remainder is not rendered and a notice is shown
- **Open in IDE diff** loads full base and head file content and opens IDEA's native two-pane diff

### Pipelines and job logs

- Lists every pipeline related to the MR above the job table; selecting a pipeline shows its jobs
- Columns: `#`, state, trigger, origin, SHA, created. The trigger column uses a right arrow (`feature/x → dev`); origin is one of `merge`, `MR check`, `push`, `target branch`, `schedule`, falling back to GitLab's raw `source` when undetermined
- Job actions: run (`manual`), retry (`failed` / `canceled` / `success`), cancel (`running` / `pending`)
- Pipeline actions: retry, cancel, open in browser
- Pipeline watch: polls by pipeline id every 10 seconds, pushes state changes to the UI, sends a notification on completion; default timeout 30 minutes, configurable
- Job log: per-line colouring (errors red, warnings orange, success green, group markers grey, commands blue), ANSI escapes and `\r` removed, Ctrl+wheel font zoom, jump to end, copy all, optional auto-refresh while running, tail limited to 4000 lines

### Creating a merge request

- Source and target are editable drop-downs tagged `[local·pushed]` / `[local·not pushed]` / `[remote only]`
- Source defaults to the current branch
- Target ordering by source prefix: `feature/*`, `bugfix/*` → `dev`, `develop`, `development`; `hotfix/*`, `fix/*` → `master`, `main`, `prod`, `production`; `release/*` → `master`, `main`
- Title defaults to `Merge branch '<source>' into '<target>'`; both title and description templates are configurable
- When local commits are ahead of the remote, `git push -u origin <branch>` runs automatically; no forced push
- A rejected push aborts creation and asks you to pull first

### Post-merge local actions

- Runs `git fetch --all --prune`
- Optional: check out the target branch (off by default)
- Optional: delete the merged local source branch (off by default)
- Steps run independently; a failure does not affect the merge result

### UI language

Values: follow system (default), Chinese, English. A restart is required. Bundles live in `src/main/resources/messages/`.

## Requirements

- IntelliJ IDEA 2026.2+
- JDK 25 (for compilation)
- Gradle via the project wrapper; no separate installation needed
- GitLab Personal Access Token with the `api` scope

## Build

```powershell
.\gradlew.bat buildPlugin
```

Output: `build\distributions\GitLabPlus-<version>.zip`, containing `GitLabPlus/lib/GitLabPlus-<version>.jar`.

The platform dependency points at a local IDEA installation through `gradle.properties`:

```properties
platformLocalPath=D:/IDEA/2026.2
```

Override it with `-PplatformLocalPath=...` or `~/.gradle/gradle.properties`. To download the distribution instead, replace `local(...)` with `intellijIdea("2026.2")` in `build.gradle.kts`.

Single version source: `pluginVersion` in `gradle.properties` drives the jar name, the zip name and `<version>` in `plugin.xml`.

Other tasks:

```powershell
.\gradlew.bat compileJava        # compile only
.\gradlew.bat buildPlugin        # produce the plugin zip
.\gradlew.bat test               # run all self-tests
.\gradlew.bat verifyPlugin       # binary compatibility check (downloads the verifier)
```

## Install and upgrade

```powershell
# IDEA is closed
powershell -ExecutionPolicy Bypass -File scripts\install.ps1

# IDEA still running: wait for it to exit, then install (up to 7200 seconds)
powershell -ExecutionPolicy Bypass -File scripts\install.ps1 -WaitSeconds 7200
```

Install directory: `%APPDATA%\JetBrains\IntelliJIdea*\plugins\GitLabPlus\`. The script takes the newest zip in `build\distributions`, extracts it and replaces the directory.

Uninstall: delete the `GitLabPlus` directory, or remove the plugin under `Settings → Plugins → Installed`.

The plugin jar is memory-mapped while IDEA runs and cannot be replaced.

## Self-tests

```powershell
.\gradlew.bat test
```

25 test classes, JUnit 5. All assertions within a class run before anything is reported, so every failing entry in that class is visible at once.

Covered: diff parsing and inline-position construction, discussion parsing, pipeline states and roles, branch ordering and push decisions (including a real non-fast-forward rejection against a constructed git remote), template rendering, Markdown rendering, ANSI stripping, log truncation and colouring, viewed-index eviction, remote URL parsing, capability trimming, bundle key parity and exact Locale matching.

`gradlew check` adds three verifications: `verifyPluginProjectConfiguration` (project configuration vs declared dependencies), `verifyPluginStructure` (archive structure and descriptor), `verifyPlugin` (binary compatibility).

## Settings

`Settings → Tools → GitLab+ Merge Tool`

| Setting | Values |
| --- | --- |
| GitLab server | Server URL, also used as the credential key |
| Access token | Stored in the IDE credential store, never in the config file; can be imported from the bundled plugin or cleared |
| Project full path | `group/sub/project`; empty means derive from the git remote |
| Project drop-down | Left list holds scanned remote projects, right list is what the drop-down shows; move and reorder freely; an empty right list shows everything |
| Post-merge actions | Check out target branch, delete merged local source branch; both off by default |
| New MR templates | Title and description templates with `${source}` `${target}` `${branchTitle}` `${project}`; empty means built-in defaults |
| Default list state | `all` / `opened` / `merged` / `closed` |
| New MR defaults | Delete source branch, squash commits |
| Pipeline watch timeout | Minutes, default 30 |
| UI language | Follow system / Chinese / English |

Config files:

| File | Content |
| --- | --- |
| `options\gitlab-plus.xml` | Server, project, templates, defaults, drop-down order |
| `options\gitlab-plus-language.xml` | UI language |

The token is stored in the IDE credential store, not in these files.

## Layout

```
build.gradle.kts             build script (IntelliJ Platform Gradle Plugin 2.x)
settings.gradle.kts          project name
gradle.properties            version, platform path, compiler settings
gradle/wrapper/              Gradle wrapper
src/main/java/com/hakil/gitlabplus/
  api/        GraphQL queries, capability probing, REST paths and client, JSON parsing
  git/        repository and remote resolution, branch listing and pushing,
              post-merge actions, process execution wrapper
  i18n/       bundle access, UI language service
  model/      records and pure functions
  service/    MergeRequestApi, MergeRequestActions, PipelineApi, GitLabService,
              review and diff services, self-check, pipeline watcher
  settings/   persistent settings, token store, bundled-plugin token import, change broadcast
  storage/    viewed-mark storage
  ui/         tool window, detail panel, dialogs, table models, renderers
src/main/resources/
  META-INF/plugin.xml
  messages/GitLabPlusBundle.properties
  messages/GitLabPlusBundle_zh_CN.properties
src/test/java/test/          25 JUnit 5 test classes
docs/api-contracts.md        implemented endpoints and parameter rules
scripts/install.ps1          install (supports -WaitSeconds)
```

## Limits

- Authentication is Personal Access Token only; single server, single account; no OAuth
- No branch checkout; use IDEA's built-in Git
- Diffs are fetched in one `/changes` call, no pagination; 3000-line rendering cap per file
- No update site; upgrades require reinstalling
- Up to 30 pipelines are listed
- Job logs render only the last 4000 lines
- Viewed marks are capped at 500 entries, evicted oldest-first

## Relationship to the bundled GitLab plugin

- Both can be enabled at the same time; neither modifies the other's files or settings
- "Import token from the bundled plugin" reads the account list from `options\gitlab.xml` and reads credentials through the platform `PasswordSafe` using `CredentialAttributes("GitLab", accountId)`
- If the bundled plugin signs in through OAuth, the imported token expires

## Versions

| Version | Content |
| --- | --- |
| 0.2.0 | Service layer split by resource; timeouts for HTTP and git calls; pipeline watcher on a dedicated scheduler; reviewers selected from project members; system entries filtered from discussions; diff supports `diff_refs` fallback and native IDE comparison; UI strings internationalised with a language switch |
| 0.1.0 | List, details, actions, review, diff with inline comments, local git refresh after merge |
