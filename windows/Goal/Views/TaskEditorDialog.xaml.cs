using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Goal.Models;
using Goal.Services;

namespace Goal.Views;

public partial class TaskEditorDialog : UserControl
{
    private readonly TaskItem? _editing;
    private string _mode = "submit";

    public event Action<bool>? Completed;

    public TaskEditorDialog(TaskItem? task = null)
    {
        InitializeComponent();
        _editing = task;
        if (task is null)
        {
            HeaderText.Text = "新建计划";
            SaveButton.Content = "创建计划";
        }
        else
        {
            HeaderText.Text = "编辑计划";
            SaveButton.Content = "保存修改";
            TitleBox.Text = task.Title;
            NoteBox.Text = task.Note;
            DueBox.Text = task.DueDate ?? "";
            if (task.IsDuration)
            {
                ModeDuration.IsChecked = true;
                DurationBox.Text = Math.Max(1, (int)Math.Ceiling(task.Target / 60.0)).ToString(CultureInfo.InvariantCulture);
            }
            else if (task.IsVariation)
            {
                ModeVariation.IsChecked = true;
                VariationTargetBox.Text = task.Target.ToString(CultureInfo.InvariantCulture);
                UnitBox.Text = task.Unit;
            }
            else ModeSubmit.IsChecked = true;
        }
        UpdateModePanels();
        Loaded += (_, _) => TitleBox.Focus();
        PreviewKeyDown += (_, e) =>
        {
            if (e.Key == System.Windows.Input.Key.Escape)
            {
                e.Handled = true;
                Completed?.Invoke(false);
            }
        };
    }

    private void Mode_Checked(object sender, RoutedEventArgs e)
    {
        if (!IsInitialized) return;
        _mode = (sender as RadioButton)?.Tag as string ?? "submit";
        UpdateModePanels();
    }

    private void UpdateModePanels()
    {
        DurationPanel.Visibility = _mode == "duration" ? Visibility.Visible : Visibility.Collapsed;
        VariationPanel.Visibility = _mode == "variation" ? Visibility.Visible : Visibility.Collapsed;
        ModeHint.Text = _mode switch
        {
            "duration" => "达到设定时长后自动完成。",
            "variation" => "达到目标数值后自动完成。",
            _ => "提交文件、视频、音频或图片后完成。"
        };
    }

    private void QuickMinutes_Click(object sender, RoutedEventArgs e)
    {
        if (sender is Button button && button.Tag is string value) DurationBox.Text = value;
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => Completed?.Invoke(false);

    private void Save_Click(object sender, RoutedEventArgs e)
    {
        var title = TitleBox.Text.Trim();
        if (title.Length == 0)
        {
            ShowError("给计划起一个名字吧。");
            TitleBox.Focus();
            return;
        }
        if (title.Length > 80)
        {
            ShowError("计划名称最多 80 个字符。");
            return;
        }
        var note = NoteBox.Text.Trim();
        string? due = null;
        if (!string.IsNullOrWhiteSpace(DueBox.Text))
        {
            if (!DateTime.TryParse(DueBox.Text.Trim(), CultureInfo.CurrentCulture, DateTimeStyles.None, out var parsed) &&
                !DateTime.TryParseExact(DueBox.Text.Trim(), new[] { "yyyy-MM-dd", "yyyy/MM/dd", "MM-dd" },
                    CultureInfo.InvariantCulture, DateTimeStyles.None, out parsed))
            {
                ShowError("截止日期格式不正确，请使用 2026-11-05。");
                return;
            }
            due = parsed.ToString("yyyy-MM-dd");
        }

        var store = GoalStore.Current;
        if (_mode == "duration")
        {
            if (!int.TryParse(DurationBox.Text.Trim(), out var minutes) || minutes < 1 || minutes > 1440)
            {
                ShowError("时长请输入 1–1440 之间的整数分钟。");
                return;
            }
            if (_editing is null)
                store.AddTask(title, note, "duration", minutes * 60, "次", due);
            else
                store.UpdateTask(_editing, title, note, "duration", minutes * 60, "次", due);
        }
        else if (_mode == "variation")
        {
            if (!int.TryParse(VariationTargetBox.Text.Trim(), out var target) || target < 1 || target > 100000)
            {
                ShowError("目标数值请输入 1–100000 之间的整数。");
                return;
            }
            var unit = string.IsNullOrWhiteSpace(UnitBox.Text) ? "次" : UnitBox.Text.Trim();
            if (_editing is null)
                store.AddTask(title, note, "variation", target, unit, due);
            else
                store.UpdateTask(_editing, title, note, "variation", target, unit, due);
        }
        else
        {
            if (_editing is null) store.AddTask(title, note, "submit", 0, "次", due);
            else store.UpdateTask(_editing, title, note, "submit", 0, "次", due);
        }
        Completed?.Invoke(true);
    }

    private void ShowError(string message)
    {
        ErrorText.Text = message;
        ErrorText.Visibility = Visibility.Visible;
    }
}
