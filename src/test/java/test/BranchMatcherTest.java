package test;

import com.hakil.gitlabplus.model.BranchMatcher;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

/**
 * 分支输入匹配：层级、排序与边界。
 *
 * <p>核心场景：输入 {@code dev} 应同时命中 {@code dev}、{@code dev-1} 与 {@code 11-dev}，
 * 且完全相等排最前、前缀次之、包含最后。</p>
 */
class BranchMatcherTest extends SuiteTestSupport {

  private static final List<String> CANDIDATES = List.of(
      "main", "dev", "dev-1", "develop", "11-dev", "feature/dev-login", "release/1.0");

  @Test
  void matchesThreeLevelsInOrder() {
    TestAssert.eq("输入 dev 的匹配结果",
                  List.of("dev", "dev-1", "develop", "11-dev", "feature/dev-login"),
                  BranchMatcher.filter(CANDIDATES, "dev"));
  }

  @Test
  void exactMatchComesFirst() {
    List<String> result = BranchMatcher.filter(List.of("adev", "dev", "dev-x"), "dev");
    TestAssert.eq("完全相等排最前", "dev", result.get(0));
  }

  @Test
  void prefixBeforeContains() {
    List<String> result = BranchMatcher.filter(List.of("x-dev", "dev-x"), "dev");
    TestAssert.eq("前缀先于包含", List.of("dev-x", "x-dev"), result);
  }

  @Test
  void shorterNameFirstWithinLevel() {
    List<String> result = BranchMatcher.filter(List.of("dev-1-beta", "dev-1"), "dev");
    TestAssert.eq("同层短的在前", List.of("dev-1", "dev-1-beta"), result);
  }

  @Test
  void caseInsensitiveMatch() {
    TestAssert.eq("大写输入命中", List.of("dev", "dev-1"), BranchMatcher.filter(List.of("dev", "dev-1"), "DEV"));
    TestAssert.eq("候选大写也能命中", List.of("DEV"), BranchMatcher.filter(List.of("DEV"), "dev"));
  }

  @Test
  void blankQueryReturnsAll() {
    TestAssert.eq("空串返回全部", CANDIDATES, BranchMatcher.filter(CANDIDATES, ""));
    TestAssert.eq("空白返回全部", CANDIDATES, BranchMatcher.filter(CANDIDATES, "   "));
    TestAssert.eq("null 返回全部", CANDIDATES, BranchMatcher.filter(CANDIDATES, null));
  }

  @Test
  void noMatchReturnsEmpty() {
    TestAssert.eq("无匹配返回空列表", List.of(), BranchMatcher.filter(CANDIDATES, "zzz"));
    TestAssert.eq("空候选返回空列表", List.of(), BranchMatcher.filter(List.of(), "dev"));
    TestAssert.eq("候选为 null 返回空列表", List.of(), BranchMatcher.filter(null, "dev"));
  }

  @Test
  void nullElementsAreSkipped() {
    TestAssert.eq("null 元素被跳过", List.of("dev"),
                  BranchMatcher.filter(Arrays.asList(null, "dev", null), "dev"));
    TestAssert.eq("全部为 null 返回空", List.of(),
                  BranchMatcher.filter(Arrays.asList(null, null), "dev"));
  }

  @Test
  void queryIsTrimmed() {
    TestAssert.eq("输入首尾空白被忽略", List.of("dev"),
                  BranchMatcher.filter(List.of("dev", "main"), "  dev  "));
  }

  @Test
  void displayTextIsStrippedBeforeMatching() {
    // 回归：选中项显示为 "[本地·已推送] dev"，若直接当查询串，列表会被清空、
    // 调用方随后读到空的分支名（新建 MR 目标分支为空的现场问题）
    TestAssert.eq("剥离已推送标签", "dev", BranchMatcher.queryOf("[本地·已推送] dev"));
    TestAssert.eq("剥离未推送标签", "dev", BranchMatcher.queryOf("[本地·未推送] dev"));
    TestAssert.eq("剥离仅远程标签", "2.0.0", BranchMatcher.queryOf("[仅远程] 2.0.0"));
    TestAssert.eq("纯分支名原样", "dev", BranchMatcher.queryOf("dev"));
    TestAssert.eq("未闭合方括号原样", "[dev", BranchMatcher.queryOf("[dev"));
    TestAssert.eq("null 返回空", "", BranchMatcher.queryOf(null));
    TestAssert.eq("空串返回空", "", BranchMatcher.queryOf("  "));

    List<String> matched =
        BranchMatcher.filter(List.of("dev", "dev-1", "master"),
                             BranchMatcher.queryOf("[本地·已推送] dev"));
    TestAssert.eq("显示文本过滤后仍有候选", List.of("dev", "dev-1"), matched);
  }

  @Test
  void sanitizeKeepsVisibleAsciiOnly() {
    TestAssert.eq("普通分支名原样", "feature/login-1.0", BranchMatcher.sanitize("feature/login-1.0"));
    TestAssert.eq("中文被丢弃", "dev-", BranchMatcher.sanitize("dev-开发"));
    TestAssert.eq("全中文返回空", "", BranchMatcher.sanitize("开发分支"));
    TestAssert.eq("空格被丢弃", "dev1", BranchMatcher.sanitize("dev 1"));
    TestAssert.eq("控制字符被丢弃", "dev", BranchMatcher.sanitize("dev\t\n"));
    TestAssert.eq("null 返回空", "", BranchMatcher.sanitize(null));
    TestAssert.eq("空串返回空", "", BranchMatcher.sanitize(""));
    TestAssert.eq("保留符号", "a-b_c.d~e/f", BranchMatcher.sanitize("a-b_c.d~e/f"));
    TestAssert.eq("全角字符被丢弃", "dev", BranchMatcher.sanitize("dev\uFF44\uFF45\uFF56"));
  }

  @Test
  void preservesOriginalOrderForEqualRank() {
    // 同层级同长度时保持调用方给的顺序（当前分支置顶等依赖这一点）
    TestAssert.eq("同层同长保持原序", List.of("dev-a", "dev-b", "dev-c"),
                  BranchMatcher.filter(List.of("dev-a", "dev-b", "dev-c"), "dev"));
  }
}
