package test;

import com.hakil.gitlabplus.model.BranchOption;
import org.junit.jupiter.api.Test;

/**
 * 分支下拉项的值/展示分离。
 *
 * <p>回归现场问题：输入关键字后从下拉选择分支，输入框变成 {@code "[] dev"} 并提示
 * 「源/目标分支不能为空」。原因是 {@code toString()} 返回带中文标签的文本，
 * 被输入白名单当作用户输入丢弃。因此这里断言两点：</p>
 * <ul>
 *   <li>{@code toString()} 必须是纯分支名（会被写进编辑器文档）</li>
 *   <li>标签只出现在 {@code displayText()} 中（仅供渲染器使用）</li>
 * </ul>
 */
class BranchOptionTest extends SuiteTestSupport {

  @Test
  void toStringIsPureBranchName() {
    TestAssert.eq("已推送分支 toString 为分支名", "dev",
                  new BranchOption("dev", true, true).toString());
    TestAssert.eq("未推送分支 toString 为分支名", "feature/x",
                  new BranchOption("feature/x", true, false).toString());
    TestAssert.eq("仅远程分支 toString 为分支名", "2.0.0",
                  new BranchOption("2.0.0", false, false).toString());
    TestAssert.eq("name 为 null 时返回空串", "",
                  new BranchOption(null, true, true).toString());
  }

  @Test
  void toStringContainsNoNonAscii() {
    for (BranchOption option : java.util.List.of(
        new BranchOption("dev", true, true),
        new BranchOption("dev", true, false),
        new BranchOption("dev", false, false))) {
      String text = option.toString();
      boolean asciiOnly = text.chars().allMatch(c -> c > 0x20 && c < 0x7F);
      TestAssert.isTrue("toString 仅含可见 ASCII：" + text, asciiOnly);
    }
  }

  @Test
  void displayTextCarriesLabel() {
    TestAssert.eq("已推送展示带标签", "[" + label("localPushed") + "] dev",
                  new BranchOption("dev", true, true).displayText());
    TestAssert.eq("未推送展示带标签", "[" + label("localUnpushed") + "] dev",
                  new BranchOption("dev", true, false).displayText());
    TestAssert.eq("仅远程展示带标签", "[" + label("remoteOnly") + "] 2.0.0",
                  new BranchOption("2.0.0", false, false).displayText());
  }

  @Test
  void recordAccessorsExposeState() {
    BranchOption option = new BranchOption("dev", true, false);
    TestAssert.eq("name 可读", "dev", option.name());
    TestAssert.eq("local 可读", true, option.local());
    TestAssert.eq("pushed 可读", false, option.pushed());
    TestAssert.eq("同值相等", new BranchOption("dev", true, false), option);
  }

  private static String label(String key) {
    return com.hakil.gitlabplus.i18n.GitLabPlusBundle.message("create.branchLabel." + key);
  }
}
