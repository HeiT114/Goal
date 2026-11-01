using System.ComponentModel;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Goal.Models;
using Goal.Services;

namespace Goal.Controls;

public partial class TaskCard : UserControl
{
    private TaskItem? _task;

    public TaskCard()
    {
        InitializeComponent();
        DataContextChanged += OnDataContextChanged;
        Loaded += OnLoaded;
        Unloaded += OnUnloaded;
    }

    public TaskCard(TaskItem task) : this()
    {
        DataContext = task;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        Opacity = 0;
        RenderTransform = new TranslateTransform(0, 10);
        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        BeginAnimation(OpacityProperty, new DoubleAnimation(1, TimeSpan.FromMilliseconds(260)) { EasingFunction = ease });
        ((TranslateTransform)RenderTransform).BeginAnimation(TranslateTransform.YProperty,
            new DoubleAnimation(0, TimeSpan.FromMilliseconds(320)) { EasingFunction = ease });
    }

    private void OnUnloaded(object sender, RoutedEventArgs e)
    {
        if (_task != null) _task.PropertyChanged -= Task_PropertyChanged;
    }

    private void OnDataContextChanged(object sender, DependencyPropertyChangedEventArgs e)
    {
        if (_task != null) _task.PropertyChanged -= Task_PropertyChanged;
        _task = e.NewValue as TaskItem;
        if (_task != null) _task.PropertyChanged += Task_PropertyChanged;
        UpdateVisuals();
    }

    private void Task_PropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName is nameof(TaskItem.Done) or nameof(TaskItem.Mode) or nameof(TaskItem.TimerStartedAt)
            or nameof(TaskItem.Proofs) or nameof(TaskItem.ProofLabel) or null)
            UpdateVisuals();
        if (e.PropertyName is nameof(TaskItem.DueLabel) or nameof(TaskItem.DueTone))
            UpdateDueTone();
    }

    private void UpdateVisuals()
    {
        if (_task is null) return;
        DoneBadge.Visibility = _task.Done ? Visibility.Visible : Visibility.Collapsed;
        TitleText.TextDecorations = _task.Done ? TextDecorations.Strikethrough : null;
        TitleText.Foreground = _task.Done
            ? (Brush)FindResource("MutedBrush")
            : (Brush)FindResource("InkBrush");
        Card.Opacity = _task.Done ? 0.72 : 1;
        NoteText.Visibility = _task.HasNote ? Visibility.Visible : Visibility.Collapsed;

        var doneTemplate = DoneButton.Template;
        if (doneTemplate != null)
        {
            var fill = doneTemplate.FindName("Fill", DoneButton) as FrameworkElement;
            var check = doneTemplate.FindName("Check", DoneButton) as FrameworkElement;
            var ring = doneTemplate.FindName("Ring", DoneButton) as FrameworkElement;
            if (fill != null) fill.Opacity = _task.Done ? 1 : 0;
            if (check != null) check.Opacity = _task.Done ? 1 : 0;
            if (ring != null) ring.Visibility = _task.Done ? Visibility.Collapsed : Visibility.Visible;
        }

        if (!_task.Done)
        {
            TitleText.Foreground = (Brush)FindResource("InkBrush");
            TitleText.TextDecorations = null;
        }

        TaskProgress.Visibility = _task.IsSubmit ? Visibility.Collapsed : Visibility.Visible;
        ProofText.Visibility = _task.HasProofs ? Visibility.Visible : Visibility.Collapsed;
        ProofText.Text = _task.HasProofs ? $"材料 · {_task.Proofs.Count} 份" : string.Empty;
        ToggleMenuItem.Header = _task.Done ? "恢复为进行中" : "一键完成";
        DurationActionsVisibility();
        UpdateDurationControls();
        UpdateDueTone();
    }

    private void DurationActionsVisibility()
    {
        if (_task is null) return;
        TimerButton.Visibility = _task.IsDuration ? Visibility.Visible : Visibility.Collapsed;
        VariationActions.Visibility = _task.IsVariation ? Visibility.Visible : Visibility.Collapsed;
        SubmitButton.Visibility = _task.IsSubmit ? Visibility.Visible : Visibility.Collapsed;
        var showActions = !_task.Done;
        TimerButton.Visibility = showActions && _task.IsDuration ? Visibility.Visible : Visibility.Collapsed;
        VariationActions.Visibility = showActions && _task.IsVariation ? Visibility.Visible : Visibility.Collapsed;
        SubmitButton.Visibility = showActions && _task.IsSubmit ? Visibility.Visible : Visibility.Collapsed;
        OneKeyButton.Visibility = showActions ? Visibility.Visible : Visibility.Collapsed;
    }

    private void UpdateDurationControls()
    {
        if (_task is null) return;
        TimerLabel.Text = _task.IsRunning ? "暂停计时" : (_task.Progress > 0 ? "继续计时" : "开始计时");
        TimerGlyph.Text = _task.IsRunning ? "\uE769" : "\uE768";
    }

    private void UpdateDueTone()
    {
        if (_task is null) return;
        StatusText.Foreground = _task.DueTone == "danger"
            ? (Brush)FindResource("DangerBrush")
            : (Brush)FindResource("MutedBrush");
    }

    private async void DoneButton_Click(object sender, RoutedEventArgs e)
    {
        if (_task is null) return;
        if (!_task.Done && _task.IsSubmit)
        {
            if (_task.HasProofs)
            {
                GoalStore.Current.Complete(_task, "提交");
                return;
            }
            var main = Window.GetWindow(this) as MainWindow;
            if (main is not null) await main.SubmitWithProofAsync(_task);
            return;
        }
        GoalStore.Current.ToggleDone(_task);
    }

    private async void TimerButton_Click(object sender, RoutedEventArgs e)
    {
        if (_task is null) return;
        if (_task.IsRunning) GoalStore.Current.PauseTimer(_task);
        else GoalStore.Current.StartTimer(_task);
        await Task.CompletedTask;
    }

    private void Plus_Click(object sender, RoutedEventArgs e)
    {
        if (_task is not null) GoalStore.Current.Increment(_task, 1);
    }

    private void Minus_Click(object sender, RoutedEventArgs e)
    {
        if (_task is not null) GoalStore.Current.Increment(_task, -1);
    }

    private async void SubmitButton_Click(object sender, RoutedEventArgs e)
    {
        if (_task is null) return;
        if (_task.HasProofs)
        {
            GoalStore.Current.Complete(_task, "提交");
            return;
        }
        var main = Window.GetWindow(this) as MainWindow;
        if (main is null) return;
        await main.SubmitWithProofAsync(_task);
    }

    private async void OneKeyButton_Click(object sender, RoutedEventArgs e) => await OneKeyCompleteAsync();

    private async Task OneKeyCompleteAsync()
    {
        if (_task is null) return;
        var main = Window.GetWindow(this) as MainWindow;
        if (main != null && !await main.ConfirmAsync("一键完成", "这次不经过「" + _task.ModeLabel + "」方式，直接把它标记为完成？", "一键完成"))
            return;
        GoalStore.Current.Complete(_task, "一键完成");
    }

    private async void ToggleMenuItem_Click(object sender, RoutedEventArgs e)
    {
        if (_task is null) return;
        if (_task.Done)
        {
            GoalStore.Current.Reopen(_task);
            return;
        }
        await OneKeyCompleteAsync();
    }

    private void ProofText_MouseLeftButtonUp(object sender, MouseButtonEventArgs e)
    {
        if (_task is not null) ProofService.OpenFolder(_task);
    }

    private void Edit_Click(object sender, RoutedEventArgs e)
    {
        if (_task is null) return;
        (Window.GetWindow(this) as MainWindow)?.ShowTaskEditor(_task);
    }

    private async void Delete_Click(object sender, RoutedEventArgs e)
    {
        if (_task is null) return;
        var main = Window.GetWindow(this) as MainWindow;
        if (main != null && !await main.ConfirmAsync("删除计划", "「" + _task.Title + "」将被永久删除，无法撤销。", "删除"))
            return;
        GoalStore.Current.DeleteTask(_task);
    }
}
