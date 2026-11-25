package com.hakil.gitlabplus.model;

import com.hakil.gitlabplus.i18n.GitLabPlusBundle;

/**
 * 分支下拉项：值（分支名）与展示（带来源标签）分离。
 *
 * <p>为什么必须分离：可编辑下拉的编辑器显示的是 {@code toString()}。若 {@code toString()}
 * 返回带中文标签的文本，输入框的白名单过滤器会把这些中文字符当作用户输入丢弃，
 * 文本退化为 {@code "[] dev"}，读回时被判为空分支。</p>
 *
 * <p>因此：{@code toString()} 只返回分支名（纯 ASCII），标签由列表渲染器使用
 * {@link #displayText()} 显示。</p>
 */
public record BranchOption(String name, boolean local, boolean pushed) {

  @Override
  public String toString() {
    return name == null ? "" : name;
  }

  /** 列表与弹出层中的展示文本，如 {@code "[本地·已推送] dev"}。 */
  public String displayText() {
    return "[" + label() + "] " + toString();
  }

  private String label() {
    if (!local) {
      return GitLabPlusBundle.message("create.branchLabel.remoteOnly");
    }
    return pushed
           ? GitLabPlusBundle.message("create.branchLabel.localPushed")
           : GitLabPlusBundle.message("create.branchLabel.localUnpushed");
  }
}
