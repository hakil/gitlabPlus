package com.hakil.gitlabplus.ui;

import com.hakil.gitlabplus.git.BranchLister;
import com.hakil.gitlabplus.i18n.GitLabPlusBundle;
import com.hakil.gitlabplus.git.BranchPusher;
import com.hakil.gitlabplus.model.BranchMatcher;
import com.hakil.gitlabplus.model.BranchOption;
import com.hakil.gitlabplus.model.BranchOrdering;
import com.hakil.gitlabplus.model.TemplateRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 创建 merge request 对话框（对齐官方 create MR 的字段集）。
 *
 * <p>源 / 目标分支为可编辑下拉，并<b>区分本地与远程</b>：
 * 显示为 <code>[本地·已推送]</code> / <code>[本地·未推送]</code> / <code>[仅远程]</code>。</p>
 *
 * <p>选中「本地·未推送」分支时会在对话框内提示：创建前需先推送，
 * 而推送被拒绝（远程有新提交）时会停止并要求先 Pull 解决冲突。</p>
 */
public final class CreateMergeRequestDialog extends DialogWrapper {

  /** 标记该 combo 已有一个排队中的过滤任务，避免连续输入堆积。 */
  private static final String KEY_FILTER_SCHEDULED = "gitlabplus.filterScheduled";

  private final JComboBox<BranchOption> sourceCombo = new JComboBox<>();
  private final JComboBox<BranchOption> targetCombo = new JComboBox<>();  private final JTextField titleField = new JTextField(32);
  private final JTextArea descriptionArea = new JTextArea(6, 40);
  private final JLabel hintLabel = new JLabel(" ");
  private final JCheckBox removeSourceCheck = new JCheckBox(GitLabPlusBundle.message("create.removeSource"), true);
  private final JCheckBox squashCheck = new JCheckBox(GitLabPlusBundle.message("create.squash"), false);

  private final String titleTemplate;
  private final Map<String, String> variables;
  private final File workDir;
  /** 用户是否仍在使用自动生成的标题（手改后不再跟随目标分支）。 */
  private boolean titleAutoFilled = true;
  private boolean updatingTitle = false;
  /** 全量分支候选：输入过滤时从这里取，过滤不改变原始顺序。 */
  private final List<BranchOption> sourceItems = new ArrayList<>();
  private final List<BranchOption> targetItems = new ArrayList<>();
  /** 防止重建下拉模型时编辑器文本变更再次触发过滤。 */
  private boolean filteringBranches;

  public CreateMergeRequestDialog(Project project,
                                  BranchLister.Branches branches,
                                  File workDir,
                                  String currentBranch,
                                  String defaultTargetBranch,
                                  String titleTemplate,
                                  String descriptionTemplate,
                                  Map<String, String> variables,
                                  boolean defaultRemoveSource,
                                  boolean defaultSquash) {
    super(project, true);
    this.workDir = workDir;
    removeSourceCheck.setSelected(defaultRemoveSource);
    squashCheck.setSelected(defaultSquash);
    this.titleTemplate = titleTemplate == null ? "" : titleTemplate;
    this.variables = variables == null ? Map.of() : variables;
    fillBranches(branches, currentBranch, defaultTargetBranch);
    descriptionArea.setText(TemplateRenderer.render(descriptionTemplate, this.variables));
    descriptionArea.setCaretPosition(0);
    descriptionArea.setLineWrap(true);
    descriptionArea.setWrapStyleWord(true);
    titleField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        markManual();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        markManual();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        markManual();
      }
    });
    setTitle(GitLabPlusBundle.message("create.title"));
    init();
    updateHint();
    refreshAutoTitle();
  }

  /** 用户手动编辑标题后，就不再随目标分支重算。 */
  private void markManual() {
    if (!updatingTitle) {
      titleAutoFilled = false;
    }
  }

  /** 根据当前源/目标分支重新渲染标题（仅当用户未手改）。 */
  private void refreshAutoTitle() {
    if (!titleAutoFilled) {
      return;
    }
    Map<String, String> vars = new LinkedHashMap<>(variables);
    vars.put("source", sourceBranch());
    vars.put("target", targetBranch());
    vars.put("branchTitle", TemplateRenderer.branchTitle(sourceBranch()));
    updatingTitle = true;
    try {
      titleField.setText(TemplateRenderer.render(titleTemplate, vars).trim());
      titleField.setCaretPosition(0);
    }
    finally {
      updatingTitle = false;
    }
  }

  /**
   * 列宽由 {@link CreateMergeRequestForm} 统一拉伸，这里只给下拉一个初始宽度。
   *
   * <p>不为标题框设 {@code setPreferredSize}：文本组件的宽度由列数决定，显式尺寸不生效，
   * 反而会让它比下拉窄一截。</p>
   */
  /** 列表与弹出层渲染：显示带来源标签的文本，编辑器里仍只显示分支名。 */
  private static final class BranchCellRenderer extends DefaultListCellRenderer {
    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean selected, boolean focused) {
      Component component =
          super.getListCellRendererComponent(list, value, index, selected, focused);
      if (component instanceof JLabel label && value instanceof BranchOption option) {
        label.setText(option.displayText());
      }
      return component;
    }
  }

  private void fillBranches(BranchLister.Branches branches,
                            String currentBranch,
                            String defaultTarget) {
    sourceCombo.setEditable(true);
    targetCombo.setEditable(true);
    sourceCombo.setRenderer(new BranchCellRenderer());
    targetCombo.setRenderer(new BranchCellRenderer());
    int fieldWidth = 560;
    sourceCombo.setPreferredSize(new Dimension(fieldWidth, sourceCombo.getPreferredSize().height));
    targetCombo.setPreferredSize(new Dimension(fieldWidth, targetCombo.getPreferredSize().height));
    sourceCombo.setMinimumSize(new Dimension(320, sourceCombo.getPreferredSize().height));
    targetCombo.setMinimumSize(new Dimension(320, targetCombo.getPreferredSize().height));

    List<String> orderedTargets = List.of();
    if (branches != null) {
      for (String name : BranchOrdering.orderSources(branches.sourceCandidates(), currentBranch)) {
        sourceItems.add(new BranchOption(name, branches.isLocal(name), branches.isPushed(name)));
      }
      // 目标分支：场景推荐 → 上次使用 → 主干优先级 → 其余，减少手动选择
      orderedTargets =
          BranchOrdering.orderTargets(branches.targetCandidates(), defaultTarget, currentBranch);
      for (String name : orderedTargets) {
        targetItems.add(new BranchOption(name, branches.isLocal(name), branches.isPushed(name)));
      }
    }
    setItems(sourceCombo, sourceItems);
    setItems(targetCombo, targetItems);

    selectByBranchName(sourceCombo, currentBranch);
    selectByBranchName(targetCombo,
                       BranchOrdering.pickTarget(orderedTargets, defaultTarget, currentBranch));

    // 让弹出层按最长项估算宽度，避免长分支名被截断
    applyPrototype(sourceCombo);
    applyPrototype(targetCombo);

    sourceCombo.addActionListener(e -> {
      if (filteringBranches) {
        return;
      }
      updateHint();
      refreshAutoTitle();
    });
    targetCombo.addActionListener(e -> {
      if (filteringBranches) {
        return;
      }
      updateHint();
      refreshAutoTitle();
    });

    // 必须在选中与 prototype 之后安装：选中会改动编辑器文本，装早了会立刻触发一次过滤
    installInputFilter(sourceCombo, sourceItems);
    installInputFilter(targetCombo, targetItems);
  }

  private static void setItems(JComboBox<BranchOption> combo, List<BranchOption> items) {
    DefaultComboBoxModel<BranchOption> model = new DefaultComboBoxModel<>();
    for (BranchOption item : items) {
      model.addElement(item);
    }
    combo.setModel(model);
  }

  /**
   * 输入即过滤：按 {@link BranchMatcher} 的层级规则把下拉项缩到匹配项。
   *
   * <p>重建 model 会重置编辑器内容，因此保存文本与光标后恢复；恢复动作再次触发
   * 文档事件，由 {@code filteringBranches} 拦住，否则会无限递归。</p>
   */
  private void installInputFilter(JComboBox<BranchOption> combo, List<BranchOption> all) {
    if (!(combo.getEditor().getEditorComponent() instanceof JTextField editor)) {
      return;
    }
    // 字符白名单：中文等输入法的组合期字符不进文档，因此不产生组合事件，
    // 也就不会在打字过程中重建模型、打断输入法
    if (editor.getDocument() instanceof AbstractDocument document) {
      document.setDocumentFilter(new AsciiInputFilter());
    }
    DocumentListener listener = new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        scheduleFilter(combo, all);
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        scheduleFilter(combo, all);
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        scheduleFilter(combo, all);
      }
    };
    editor.getDocument().addDocumentListener(listener);
  }

  /**
   * 把过滤延后到事件循环。
   *
   * <p>不能在文档事件回调里直接改下拉模型：{@code setModel} 会经编辑器写回文档，
   * Swing 会抛 {@code IllegalStateException: Attempt to mutate in notification}。
   * 同一个 combo 只入队一次，执行时再读取最新文本，因此连续输入不会堆积。</p>
   */
  private void scheduleFilter(JComboBox<BranchOption> combo, List<BranchOption> all) {
    if (filteringBranches || Boolean.TRUE.equals(combo.getClientProperty(KEY_FILTER_SCHEDULED))) {
      return;
    }
    combo.putClientProperty(KEY_FILTER_SCHEDULED, Boolean.TRUE);
    SwingUtilities.invokeLater(() -> {
      combo.putClientProperty(KEY_FILTER_SCHEDULED, Boolean.FALSE);
      applyFilter(combo, all);
    });
  }

  /** 只允许可见 ASCII 进入文档（分支名不需要中文）。 */
  private static final class AsciiInputFilter extends DocumentFilter {
    @Override
    public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
        throws BadLocationException {
      String cleaned = BranchMatcher.sanitize(text);
      if (!cleaned.isEmpty()) {
        super.insertString(fb, offset, cleaned, attrs);
      }
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
        throws BadLocationException {
      super.replace(fb, offset, length, BranchMatcher.sanitize(text), attrs);
    }
  }

  private void applyFilter(JComboBox<BranchOption> combo, List<BranchOption> all) {
    if (filteringBranches) {
      return;
    }
    filteringBranches = true;
    try {
      JTextField editor = (JTextField) combo.getEditor().getEditorComponent();
      String text = editor.getText();
      int caret = editor.getCaretPosition();
      setItems(combo, filterItems(all, text));
      // setModel 之后编辑器可能被替换，重新获取再写回文本
      JTextField fresh = (JTextField) combo.getEditor().getEditorComponent();
      fresh.setText(text);
      fresh.setCaretPosition(Math.max(0, Math.min(caret, text.length())));
      if (text != null && !text.isBlank() && combo.isShowing() && !combo.isPopupVisible()
          && combo.getItemCount() > 0) {
        combo.showPopup();
      }
    }
    finally {
      filteringBranches = false;
    }
    // 重建模型期间的 ActionEvent 被守卫拦掉，这里补一次刷新
    updateHint();
    refreshAutoTitle();
  }

  private static List<BranchOption> filterItems(List<BranchOption> all, String query) {
    List<String> names = new ArrayList<>(all.size());
    for (BranchOption item : all) {
      names.add(item.name());
    }
    List<String> matched = BranchMatcher.filter(names, BranchMatcher.queryOf(query));
    List<BranchOption> result = new ArrayList<>(matched.size());
    for (String name : matched) {
      BranchOption item = findByName(all, name);
      if (item != null) {
        result.add(item);
      }
    }
    return result;
  }

  private static BranchOption findByName(List<BranchOption> items, String name) {
    if (name == null) {
      return null;
    }
    for (BranchOption item : items) {
      if (item.name().equals(name)) {
        return item;
      }
    }
    return null;
  }

  private static void applyPrototype(JComboBox<BranchOption> combo) {
    BranchOption longest = null;
    for (int i = 0; i < combo.getItemCount(); i++) {
      BranchOption item = combo.getItemAt(i);
      if (item == null) {
        continue;
      }
      // 按展示文本（含标签）估算宽度，否则弹出层显示标签后会被截断
      if (longest == null || item.displayText().length() > longest.displayText().length()) {
        longest = item;
      }
    }
    if (longest != null) {
      combo.setPrototypeDisplayValue(longest);
    }
  }

  private void selectByBranchName(JComboBox<BranchOption> combo, String branchName) {
    if (branchName != null && !branchName.isBlank()) {
      for (int i = 0; i < combo.getItemCount(); i++) {
        BranchOption item = combo.getItemAt(i);
        if (item != null && item.name().equals(branchName)) {
          combo.setSelectedIndex(i);
          return;
        }
      }
      combo.getEditor().setItem(branchName);
      return;
    }
    if (combo.getItemCount() > 0) {
      combo.setSelectedIndex(0);
    }
  }

  private void updateHint() {
    // 按最终分支名取候选：输入过滤后下拉的选中项可能不是用户正在输入的那一项
    String sourceName = comboValue(sourceCombo);
    BranchOption source = findByName(sourceItems, sourceName);
    if (source == null) {
      hintLabel.setText(sourceName.isBlank()
                            ? " "
                            : GitLabPlusBundle.message("create.hint.unknownBranch", escape(sourceName)));
      return;
    }
    if (source.local() && !source.pushed()) {
      hintLabel.setText("<html>该分支尚未推送到远程：创建前会自动 <code>git push -u origin "
                            + escape(source.name())
                            + "</code>；若远程已有新提交导致推送被拒绝，会停下并要求先 Pull 解决冲突。</html>");
    }
    else if (source.local()) {
      // 远程已存在同名分支，但本地可能还有未推送提交（含该情况的 MR 会没有任何变更）
      int ahead = workDir == null ? -1 : BranchPusher.aheadCount(workDir, source.name());
      if (ahead > 0) {
        hintLabel.setText("<html>该分支有 <b>" + ahead + "</b> 个提交尚未推送：创建前会自动 push，否则 MR 不会有任何变更。"
                              + "若推送被拒绝，会停下并要求先 Pull 解决冲突。</html>");
      }
      else {
        hintLabel.setText("<html>该分支与远程 <code>origin/" + escape(source.name()) + "</code> 已同步。</html>");
      }
    }
    else if (!source.local()) {
      hintLabel.setText("<html>该分支仅存在于远程，将直接以远程分支创建 merge request。</html>");
    }
    else {
      hintLabel.setText(" ");
    }
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JPanel panel = CreateMergeRequestForm.build(List.of(
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.source") + "：", sourceCombo, false),
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.target") + "：", targetCombo, false),
        new CreateMergeRequestForm.Row(null, hintLabel, true),
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.titleField") + "：", titleField, false),
        new CreateMergeRequestForm.Row(GitLabPlusBundle.message("create.descriptionField") + "：",
                                       new JScrollPane(descriptionArea), false, true),
        new CreateMergeRequestForm.Row(null, removeSourceCheck, true),
        new CreateMergeRequestForm.Row(null, squashCheck, true)));
    panel.setMinimumSize(CreateMergeRequestForm.minimumSize());
    panel.setPreferredSize(new Dimension(760, 460));
    return panel;
  }

  @Override
  protected @Nullable ValidationInfo doValidate() {
    if (sourceBranch().isBlank()) {
      return new ValidationInfo("源分支不能为空", sourceCombo);
    }
    if (targetBranch().isBlank()) {
      return new ValidationInfo("目标分支不能为空", targetCombo);
    }
    if (sourceBranch().equals(targetBranch())) {
      return new ValidationInfo("源分支与目标分支不能相同", targetCombo);
    }
    if (titleField.getText().isBlank()) {
      return new ValidationInfo("标题不能为空", titleField);
    }
    return null;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return targetCombo;
  }

  public String sourceBranch() {
    return comboValue(sourceCombo);
  }

  public String targetBranch() {
    return comboValue(targetCombo);
  }

  public String title() {
    return titleField.getText().trim();
  }

  public String description() {
    return descriptionArea.getText();
  }

  public boolean removeSourceBranch() {
    return removeSourceCheck.isSelected();
  }

  public boolean squash() {
    return squashCheck.isSelected();
  }

  /** 可编辑下拉：编辑器文本与选中项一致时取分支名；否则视为手输。 */
  private static String comboValue(JComboBox<BranchOption> combo) {
    Object selected = combo.getSelectedItem();
    Object edited = combo.isEditable() ? combo.getEditor().getItem() : null;
    String editedText = edited == null ? "" : edited.toString().trim();
    if (!editedText.isEmpty()) {
      // 编辑器显示的就是分支名（标签只在渲染器里出现），因此非空即为有效值
      return editedText;
    }
    return selected instanceof BranchOption item ? item.name() : "";
  }

  private static String escape(String text) {
    return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}
