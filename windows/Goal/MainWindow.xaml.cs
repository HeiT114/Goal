using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Interop;
using Microsoft.Win32;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using System.Windows.Threading;
using Goal.Models;
using Goal.Services;
using Goal.Views;

namespace Goal;

public partial class MainWindow : Window
{
    private readonly Dictionary<string, UserControl> _pages = new();
    private readonly DispatcherTimer _ticker;
    private DispatcherTimer? _toastTimer;
    private string _pageKey = "home";
    private bool _navigating;
    private int _toastVersion;

    public MainWindow()
    {
        InitializeComponent();
        ApplyWindowPlacement();

        Loaded += (_, _) =>
        {
            if (StorageService.LastLoadWarning is { Length: > 0 } warning) Toast(warning);
        };

        _ticker = new DispatcherTimer(DispatcherPriority.Background)
        {
            Interval = TimeSpan.FromSeconds(1)
        };
        _ticker.Tick += Ticker_Tick;
        _ticker.Start();

        GoalStore.Current.Changed += Store_Changed;
        GoalStore.Current.TaskCompleted += Store_TaskCompleted;
        GoalStore.Current.Notice += Store_Notice;
        StateChanged += Window_StateChanged;
        Closing += (_, _) => SaveWindowPlacement();
        Closed += Window_Closed;

        _navigating = true;
        HomeNav.IsChecked = true;
        _navigating = false;
        ShowPage("home");
        RefreshShell();
    }

    public Task<bool> ConfirmAsync(string title, string body, string primaryText, bool danger = false)
    {
        var tcs = new TaskCompletionSource<bool>();
        var dialog = new ConfirmDialog(title, body, primaryText, "取消", danger);
        dialog.Answered += confirmed =>
        {
            CloseOverlay();
            tcs.TrySetResult(confirmed);
        };
        ShowOverlay(dialog);
        return tcs.Task;
    }

    public async Task SubmitWithProofAsync(TaskItem task)
    {
        if (task.Done) return;
        var picker = new OpenFileDialog
        {
            Title = "选择完成材料 · 文件 / 视频 / 音频 / 图片",
            Filter = "所有文件|*.*",
            Multiselect = true,
            CheckFileExists = true
        };
        if (picker.ShowDialog(this) != true) return;
        var files = picker.FileNames;
        if (files.Length == 0) return;

        var preview = files.Length <= 3
            ? string.Join("、", files.Select(Path.GetFileName))
            : $"{string.Join("、", files.Take(3).Select(Path.GetFileName))} 等 {files.Length} 份";
        if (!await ConfirmAsync("提交完成", $"添加「{preview}」并提交？材料只保存在本机。", "提交完成"))
            return;

        try
        {
            var proofs = await Task.Run(() => ProofService.Copy(task, files));
            GoalStore.Current.AddProofs(task, proofs);
            GoalStore.Current.Complete(task, "提交");
        }
        catch (Exception ex)
        {
            await ShowMessageAsync("材料添加失败", ex.Message);
        }
    }

    public Task ShowMessageAsync(string title, string body)
    {
        var tcs = new TaskCompletionSource<bool>();
        var dialog = new ConfirmDialog(title, body, "知道了", null);
        dialog.Answered += _ =>
        {
            CloseOverlay();
            tcs.TrySetResult(true);
        };
        ShowOverlay(dialog);
        return tcs.Task;
    }

    public void ShowTaskEditor(TaskItem? task = null)
    {
        var isNew = task is null;
        var dialog = new TaskEditorDialog(task);
        dialog.Completed += saved =>
        {
            CloseOverlay();
            if (!saved) return;
            _pages.TryGetValue("home", out var page);
            (page as HomeView)?.Refresh();
            RefreshShell();
            Toast(isNew ? "计划已添加" : "计划已更新");
        };
        ShowOverlay(dialog);
    }

    public void ShowImportDialog(AppData data, string fileName)
    {
        var dialog = new ImportChoiceDialog(data, fileName);
        dialog.Chosen += choice =>
        {
            CloseOverlay();
            if (choice is not ("overwrite" or "merge")) return;
            try
            {
                var store = GoalStore.Current;
                store.Import(data, choice == "merge");
                ThemeService.Apply(store.Config);
                RefreshAllPages();
                Toast(choice == "merge" ? "已合并导入" : "已覆盖导入", fileName);
            }
            catch (Exception ex)
            {
                _ = ShowMessageAsync("导入失败", ex.Message);
            }
        };
        ShowOverlay(dialog);
    }

    public void ShowPluginInstallDialog(PluginPackagePreview preview)
    {
        var dialog = new PluginInstallDialog(preview);
        dialog.Dismissed += CloseOverlay;
        dialog.InstallRequested += () =>
        {
            CloseOverlay();
            try
            {
                var installed = PluginService.Current.Install(preview);
                RefreshPlugins();
                Toast("插件已安装", "「" + installed.Name + "」默认处于关闭状态。");
            }
            catch (Exception ex)
            {
                _ = ShowMessageAsync("安装失败", ex.Message);
            }
        };
        ShowOverlay(dialog);
    }

    public void ShowPluginRunner(InstalledPlugin plugin)
    {
        try
        {
            var panel = PluginService.Current.ReadPanel(plugin);
            ShowOverlay(new PluginRunnerDialog(plugin, panel));
        }
        catch (Exception ex)
        {
            _ = ShowMessageAsync("无法打开插件", ex.Message);
        }
    }

    public void ShowUpdateDialog(UpdateResult result, string prefix)
    {
        ShowOverlay(new UpdateDialog(result, prefix));
    }

    public void RefreshShell()
    {
        var store = GoalStore.Current;
        SidebarPercent.Text = store.Percent + "%";
        SidebarFraction.Text = store.DoneCount + " / " + store.TotalCount;
        SidebarProgress.Value = store.Percent;
        SidebarSummary.Text = store.TotalCount == 0
            ? "还没有计划"
            : "已完成 " + store.DoneCount + " 个 · 进行中 " + store.ActiveCount + " 个";

        var name = string.IsNullOrWhiteSpace(store.User.Name) ? "朋友" : store.User.Name.Trim();
        SidebarUserName.Text = name;
        SidebarUserInitial.Text = name.Length > 0 ? name[..1] : "朋";
    }

    public void Toast(string message, string? detail = null)
    {
        if (string.IsNullOrWhiteSpace(message)) return;

        _toastVersion++;
        var version = _toastVersion;
        ToastText.Text = message;
        ToastDetail.Text = detail ?? string.Empty;
        ToastDetail.Visibility = string.IsNullOrWhiteSpace(detail) ? Visibility.Collapsed : Visibility.Visible;
        ToastRoot.Visibility = Visibility.Visible;
        ToastRoot.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(170))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        });
        ToastTransform.BeginAnimation(TranslateTransform.YProperty, new DoubleAnimation(8, 0, TimeSpan.FromMilliseconds(220))
        {
            EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
        });

        _toastTimer?.Stop();
        var timer = new DispatcherTimer
        {
            Interval = TimeSpan.FromSeconds(string.IsNullOrWhiteSpace(detail) ? 2.8 : 3.5)
        };
        _toastTimer = timer;
        timer.Tick += (_, _) =>
        {
            timer.Stop();
            if (version != _toastVersion) return;
            var fade = new DoubleAnimation(1, 0, TimeSpan.FromMilliseconds(180));
            fade.Completed += (_, _) =>
            {
                if (version == _toastVersion) ToastRoot.Visibility = Visibility.Collapsed;
            };
            ToastRoot.BeginAnimation(OpacityProperty, fade);
        };
        timer.Start();
    }

    public void CloseOverlay()
    {
        DialogHost.BeginAnimation(OpacityProperty, null);
        ScrimLayer.BeginAnimation(OpacityProperty, null);
        DialogHost.Content = null;
        DialogHost.Opacity = 0;
        ScrimLayer.Opacity = 0;
        OverlayHost.Visibility = Visibility.Collapsed;
    }

    private void ShowOverlay(FrameworkElement content)
    {
        DialogHost.Content = content;
        OverlayHost.Visibility = Visibility.Visible;
        ScrimLayer.Opacity = 0;
        DialogHost.Opacity = 0;
        ScrimLayer.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(150)));
        DialogHost.BeginAnimation(OpacityProperty, new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(180)));

        if (DialogHost.RenderTransform is ScaleTransform scale)
        {
            scale.ScaleX = 0.96;
            scale.ScaleY = 0.96;
            var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
            scale.BeginAnimation(ScaleTransform.ScaleXProperty,
                new DoubleAnimation(0.96, 1, TimeSpan.FromMilliseconds(200)) { EasingFunction = ease });
            scale.BeginAnimation(ScaleTransform.ScaleYProperty,
                new DoubleAnimation(0.96, 1, TimeSpan.FromMilliseconds(200)) { EasingFunction = ease });
        }

        Dispatcher.BeginInvoke(new Action(() =>
        {
            content.MoveFocus(new TraversalRequest(FocusNavigationDirection.First));
        }), DispatcherPriority.Input);
    }

    private void ShowPage(string key)
    {
        if (!_pages.TryGetValue(key, out var page))
        {
            page = key switch
            {
                "plugins" => new PluginsView(),
                "settings" => new SettingsView(),
                _ => new HomeView()
            };
            _pages[key] = page;
        }

        _pageKey = key;
        PageHost.Content = page;
        CaptionText.Text = key switch
        {
            "plugins" => "插件",
            "settings" => "设置",
            _ => "主页"
        };

        page.Opacity = 0;
        var translate = new TranslateTransform(0, 8);
        page.RenderTransform = translate;
        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        page.BeginAnimation(OpacityProperty,
            new DoubleAnimation(0, 1, TimeSpan.FromMilliseconds(220)) { EasingFunction = ease });
        translate.BeginAnimation(TranslateTransform.YProperty,
            new DoubleAnimation(8, 0, TimeSpan.FromMilliseconds(260)) { EasingFunction = ease });

        _navigating = true;
        switch (key)
        {
            case "plugins": PluginsNav.IsChecked = true; break;
            case "settings": SettingsNav.IsChecked = true; break;
            default: HomeNav.IsChecked = true; break;
        }
        _navigating = false;
    }

    private void RefreshAllPages()
    {
        foreach (var page in _pages.Values)
        {
            switch (page)
            {
                case HomeView home: home.Refresh(); break;
                case PluginsView plugins: plugins.Refresh(); break;
                case SettingsView settings: settings.Refresh(); break;
            }
        }
        RefreshShell();
    }

    private void RefreshPlugins()
    {
        if (_pages.TryGetValue("plugins", out var page) && page is PluginsView view) view.Refresh();
    }

    private void Nav_Checked(object sender, RoutedEventArgs e)
    {
        if (_navigating) return;
        if ((sender as RadioButton)?.Tag is string key) ShowPage(key);
    }

    private void Profile_Click(object sender, RoutedEventArgs e)
    {
        ShowPage("settings");
    }

    private void Ticker_Tick(object? sender, EventArgs e)
    {
        GoalStore.Current.Tick();
    }

    private void Store_Changed()
    {
        RefreshShell();
    }

    private void Store_Notice(string message) => Toast(message);

    private void Store_TaskCompleted(TaskItem task, string via)
    {
        var title = task.Title.Length > 20 ? task.Title[..20] + "…" : task.Title;
        Toast("「" + title + "」已完成", via);
    }

    private void TitleBar_MouseLeftButtonDown(object sender, MouseButtonEventArgs e)
    {
        if (e.ClickCount == 2)
        {
            ToggleMaximize();
            e.Handled = true;
            return;
        }
        if (e.ButtonState != MouseButtonState.Pressed) return;
        try
        {
            DragMove();
        }
        catch (InvalidOperationException)
        {
            // 鼠标已离开拖动区域时，忽略。
        }
    }

    private void Minimize_Click(object sender, RoutedEventArgs e)
    {
        WindowState = WindowState.Minimized;
    }

    private void Maximize_Click(object sender, RoutedEventArgs e)
    {
        ToggleMaximize();
    }

    private void ToggleMaximize()
    {
        WindowState = WindowState == WindowState.Maximized ? WindowState.Normal : WindowState.Maximized;
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        Close();
    }

    private void Window_StateChanged(object? sender, EventArgs e)
    {
        var maximized = WindowState == WindowState.Maximized;
        MaximizeButton.Content = maximized ? "\uE923" : "\uE922";
        MaximizeButton.ToolTip = maximized ? "还原" : "最大化";
    }

    private void Window_PreviewKeyDown(object sender, KeyEventArgs e)
    {
        if (OverlayHost.Visibility == Visibility.Visible) return;
        if (Keyboard.Modifiers != ModifierKeys.Control) return;
        if (e.Key == Key.N)
        {
            ShowTaskEditor();
            e.Handled = true;
        }
        else if (e.Key == Key.D1)
        {
            ShowPage("home");
            e.Handled = true;
        }
        else if (e.Key == Key.D2)
        {
            ShowPage("plugins");
            e.Handled = true;
        }
        else if (e.Key == Key.D3)
        {
            ShowPage("settings");
            e.Handled = true;
        }
    }

    private void Window_Closed(object? sender, EventArgs e)
    {
        _ticker.Stop();
        _toastTimer?.Stop();
        GoalStore.Current.Changed -= Store_Changed;
        GoalStore.Current.TaskCompleted -= Store_TaskCompleted;
        GoalStore.Current.Notice -= Store_Notice;
        GoalStore.Current.Persist();
    }

    private void ApplyWindowPlacement()
    {
        var placement = WindowPlacementService.Load();
        if (placement is null) return;

        Width = Math.Max(MinWidth, placement.Width);
        Height = Math.Max(MinHeight, placement.Height);

        var left = placement.Left;
        var top = placement.Top;
        if (IsVisibleOnAnyScreen(left, top, Width, Height))
        {
            WindowStartupLocation = WindowStartupLocation.Manual;
            Left = left;
            Top = top;
        }

        if (placement.Maximized) WindowState = WindowState.Maximized;
    }

    private static bool IsVisibleOnAnyScreen(double left, double top, double width, double height)
    {
        var screenLeft = SystemParameters.VirtualScreenLeft;
        var screenTop = SystemParameters.VirtualScreenTop;
        var screenRight = screenLeft + SystemParameters.VirtualScreenWidth;
        var screenBottom = screenTop + SystemParameters.VirtualScreenHeight;

        var visibleWidth = Math.Min(left + width, screenRight) - Math.Max(left, screenLeft);
        var visibleHeight = Math.Min(top + height, screenBottom) - Math.Max(top, screenTop);
        return visibleWidth >= 240 && visibleHeight >= 160;
    }

    private void SaveWindowPlacement()
    {
        var maximized = IsWindowMaximized();
        var bounds = maximized ? RestoreBounds : new Rect(Left, Top, Width, Height);
        if (bounds.Width < 400 || bounds.Height < 300) return;
        WindowPlacementService.Save(new WindowPlacementState
        {
            Left = bounds.Left,
            Top = bounds.Top,
            Width = bounds.Width,
            Height = bounds.Height,
            Maximized = maximized
        });
    }

    private bool IsWindowMaximized()
    {
        if (WindowState == WindowState.Maximized) return true;
        try
        {
            var handle = new WindowInteropHelper(this).Handle;
            return handle != IntPtr.Zero && IsZoomed(handle);
        }
        catch
        {
            return false;
        }
    }

    [DllImport("user32.dll")]
    private static extern bool IsZoomed(IntPtr hWnd);
}