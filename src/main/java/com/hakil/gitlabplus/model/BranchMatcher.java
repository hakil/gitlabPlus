package com.hakil.gitlabplus.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 分支名匹配：把用户输入变成下拉候选的子集。
 *
 * <p>匹配分三层，层级越高越靠前：</p>
 * <ol>
 *   <li><b>完全相等</b> —— 输入 {@code dev} 命中 {@code dev}</li>
 *   <li><b>前缀匹配</b> —— 命中 {@code dev-1}、{@code develop}</li>
 *   <li><b>包含匹配</b> —— 命中 {@code 11-dev}、{@code feature/dev-login}</li>
 * </ol>
 *
 * <p>同层内按名称长度升序（越短越接近输入），长度相同按原始顺序，因此过滤后仍保留
 * 调用方给定的顺序（当前分支置顶、场景推荐在前等）。大小写不敏感。</p>
 */
public final class BranchMatcher {

  private BranchMatcher() {
  }

  /**
   * @param candidates 原始候选（顺序即同长度时的次序）
   * @param query      用户输入；空白时原样返回全部
   * @return 匹配项；无匹配返回空列表
   */
  public static List<String> filter(List<String> candidates, String query) {
    if (candidates == null || candidates.isEmpty()) {
      return List.of();
    }
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    if (needle.isEmpty()) {
      return new ArrayList<>(candidates);
    }
    List<Hit> hits = new ArrayList<>();
    for (int i = 0; i < candidates.size(); i++) {
      String name = candidates.get(i);
      if (name == null || name.isEmpty()) {
        continue;
      }
      int level = level(name.toLowerCase(Locale.ROOT), needle);
      if (level < 0) {
        continue;
      }
      hits.add(new Hit(name, level, name.length(), i));
    }
    hits.sort(Comparator.comparingInt(Hit::level)
                  .thenComparingInt(Hit::length)
                  .thenComparingInt(Hit::index));
    List<String> result = new ArrayList<>(hits.size());
    for (Hit hit : hits) {
      result.add(hit.name());
    }
    return result;
  }

  /**
   * 匹配层级：0 完全相等，1 前缀，2 包含，-1 不匹配。
   *
   * <p>参数须已转小写。</p>
   */
  static int level(String lowerName, String lowerQuery) {
    if (lowerName.equals(lowerQuery)) {
      return 0;
    }
    if (lowerName.startsWith(lowerQuery)) {
      return 1;
    }
    if (lowerName.contains(lowerQuery)) {
      return 2;
    }
    return -1;
  }

  /**
   * 输入框字符白名单：只保留可见 ASCII（0x21–0x7E），去掉空格与所有非 ASCII 字符。
   *
   * <p>目的是避开中文等输入法的组合期（composition）事件：组合过程中每次按键都会改动
   * 文档，若此时重建下拉模型会打断输入法，表现为候选词消失或字符重复。字符在进入文档前
   * 就被丢弃，文档因此不再产生组合事件。</p>
   *
   * <p>代价：不能手输非 ASCII 分支名（git 允许，但极罕见）。此类分支仍可从下拉列表选择，
   * 因为候选来自 {@code git for-each-ref} 的输出，不受此过滤影响。</p>
   */
  public static String sanitize(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c >= 0x21 && c <= 0x7E) {
        builder.append(c);
      }
    }
    return builder.toString();
  }

  private record Hit(String name, int level, int length, int index) {
  }
}
