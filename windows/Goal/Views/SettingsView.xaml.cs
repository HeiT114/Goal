using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Goal.Models;
using Goal.Services;
using Microsoft.Win32;

namespace Goal.Views;

public partial class SettingsView : UserControl
{
    private readonly Dictionary<string, RadioButton> _themeTiles = new(StringComparer.OrdinalIgnoreCase);
    private bool _loading = true;
    private bool _ready;

    public SettingsView()
    {
        InitializeComponent();
        Loaded += OnLoaded;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        if (_ready) return;
        _ready = true;
        BuildThemeTiles();
        Refresh();
    }

    public void Refresh()
    {
        if (!_ready) return;
        _loading = true;
        var store = GoalStore.Current;
        NameBox.Text = store.User.Name;
        MottoBox.Text = store.User.Motto;
        DarkToggle.IsChecked = store.Config.Dark;
        PrefixBox.Text = store.Config.UpdatePrefix;
        AccentBox.Text = store.Config.Accent;
        AccentPreview.Background = ThemeService.BrushFromHex(store.Config.Accent);
        AvatarText.Text = string.IsNullOrWhiteSpace(store.User.Name) ? "朋" : store.User.Name.Trim()[..1];
        DataPathText.Text = "数据文件：" + StorageService.DataFile;
        VersionBadgeText.Text = UpdateService.CurrentVersion;
        AccentHint.Text = "";
        AccentHint.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        foreach (var (id, tile) in _themeTiles)
            tile.IsChecked = string.Equals(id, store.Config.Theme, StringComparison.OrdinalIgnoreCase);
        _loading = false;
    }

    private void BuildThemeTiles()
    {
        ThemePanel.Children.Clear();
        _themeTiles.Clear();
        foreach (var preset in ThemeService.Presets)
        {
            var content = new StackPanel();
            var top = new StackPanel { Orientation = Orientation.Horizontal };
            top.Children.Add(new Border
            {
                Width = 15,
                Height = 15,
                CornerRadius = new CornerRadius(8),
                Background = ThemeService.BrushFromHex(preset.Accent),
                VerticalAlignment = VerticalAlignment.Center
            });
            var name = new TextBlock
            {
                Text = preset.Name,
                FontSize = 13,
                FontWeight = FontWeights.SemiBold,
                Margin = new Thickness(7, 0, 0, 0),
                VerticalAlignment = VerticalAlignment.Center
            };
            name.SetResourceReference(TextBlock.ForegroundProperty, "InkBrush");
            top.Children.Add(name);
            content.Children.Add(top);
            var desc = new TextBlock
            {
                Text = preset.Description,
                FontSize = 10.5,
                Margin = new Thickness(0, 6, 0, 0)
            };
            desc.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
            content.Children.Add(desc);

            var tile = new RadioButton
            {
                Style = (Style)FindResource("ThemeTile"),
                GroupName = "ThemeChoice",
                Tag = preset.Id,
                Content = content,
                ToolTip = preset.Description
            };
            tile.Checked += ThemeTile_Checked;
            _themeTiles[preset.Id] = tile;
            ThemePanel.Children.Add(tile);
        }
    }

    private void ThemeTile_Checked(object sender, RoutedEventArgs e)
    {
        if (_loading || !_ready) return;
        if (sender is not RadioButton tile || tile.Tag is not string id) return;
        var preset = ThemeService.Find(id);
        GoalStore.Current.UpdateConfig(c =>
        {
            c.Theme = id;
            if (preset is not null) c.Accent = preset.Accent;
        });
        ThemeService.Apply(GoalStore.Current.Config);
        _loading = true;
        AccentBox.Text = GoalStore.Current.Config.Accent;
        AccentPreview.Background = ThemeService.BrushFromHex(GoalStore.Current.Config.Accent);
        AccentHint.Text = "";
        _loading = false;
        (Window.GetWindow(this) as MainWindow)?.RefreshShell();
    }

    private void DarkToggle_Changed(object sender, RoutedEventArgs e)
    {
        if (_loading || !_ready) return;
        var dark = DarkToggle.IsChecked == true;
        GoalStore.Current.UpdateConfig(c => c.Dark = dark);
        ThemeService.Apply(GoalStore.Current.Config);
        (Window.GetWindow(this) as MainWindow)?.RefreshShell();
    }

    private void ApplyAccent_Click(object sender, RoutedEventArgs e)
    {
        var hex = StorageService.NormalizeHex(AccentBox.Text);
        if (hex is null)
        {
            AccentHint.Text = "请输入 #RGB 或 #RRGGBB。";
            AccentHint.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
            return;
        }
        GoalStore.Current.UpdateConfig(c => c.Accent = hex);
        ThemeService.Apply(GoalStore.Current.Config);
        AccentBox.Text = hex;
        AccentPreview.Background = ThemeService.BrushFromHex(hex);
        AccentHint.Text = "已应用。";
        AccentHint.SetResourceReference(TextBlock.ForegroundProperty, "SuccessBrush");
        (Window.GetWindow(this) as MainWindow)?.RefreshShell();
    }

    private void SaveProfile_Click(object sender, RoutedEventArgs e)
    {
        var name = NameBox.Text.Trim();
        if (name.Length > 24)
        {
            ProfileHint.Text = "称呼最多 24 个字符。";
            ProfileHint.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
            return;
        }
        GoalStore.Current.UpdateUser(name, MottoBox.Text);
        AvatarText.Text = string.IsNullOrWhiteSpace(name) ? "朋" : name[..1];
        ProfileHint.Text = "资料已保存。";
        ProfileHint.SetResourceReference(TextBlock.ForegroundProperty, "SuccessBrush");
        (Window.GetWindow(this) as MainWindow)?.RefreshShell();
        (Window.GetWindow(this) as MainWindow)?.Toast("个人资料已更新");
    }

    private void Export_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new SaveFileDialog
        {
            Title = "导出 goal 数据",
            Filter = "goal 数据 (*.json)|*.json|所有文件 (*.*)|*.*",
            FileName = $"goal-backup-{DateTime.Now:yyyyMMdd-HHmm}.json",
            DefaultExt = ".json",
            AddExtension = true
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            File.WriteAllText(dialog.FileName, StorageService.ExportJson(GoalStore.Current.Data));
            (Window.GetWindow(this) as MainWindow)?.Toast("已导出", dialog.FileName);
        }
        catch (Exception ex)
        {
            _ = (Window.GetWindow(this) as MainWindow)?.ShowMessageAsync("导出失败", ex.Message);
        }
    }

    private async void Import_Click(object sender, RoutedEventArgs e)
    {
        var dialog = new OpenFileDialog
        {
            Title = "导入 goal 数据",
            Filter = "goal 数据 (*.json)|*.json|所有文件 (*.*)|*.*",
            CheckFileExists = true
        };
        if (dialog.ShowDialog() != true) return;
        try
        {
            var info = new FileInfo(dialog.FileName);
            if (info.Length > 8 * 1024 * 1024) throw new InvalidDataException("文件超过 8 MB。");
            var json = File.ReadAllText(dialog.FileName);
            var data = StorageService.ParseImport(json);
            (Window.GetWindow(this) as MainWindow)?.ShowImportDialog(data, Path.GetFileName(dialog.FileName));
        }
        catch (Exception ex)
        {
            var main = Window.GetWindow(this) as MainWindow;
            if (main is not null) await main.ShowMessageAsync("无法导入", ex.Message);
        }
    }

    private async void CheckUpdate_Click(object sender, RoutedEventArgs e)
    {
        var prefix = UpdateService.NormalizePrefix(PrefixBox.Text);
        if (!Uri.TryCreate(prefix, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http"))
        {
            UpdateStatusText.Text = "请先填写有效的加速前缀，例如 https://ghproxy.net/ 。";
            UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
            return;
        }
        CheckUpdateButton.IsEnabled = false;
        UpdateStatusText.Text = "正在连接更新服务器…";
        UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        try
        {
            var result = await UpdateService.CheckAsync("windows", prefix);
            if (!string.IsNullOrEmpty(result.Error))
            {
                UpdateStatusText.Text = result.Error;
                UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
            }
            else if (result.HasUpdate)
            {
                UpdateStatusText.Text = $"发现新版本 {result.LatestVersion}。";
                UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "SuccessBrush");
                (Window.GetWindow(this) as MainWindow)?.ShowUpdateDialog(result, prefix);
            }
            else
            {
                UpdateStatusText.Text = $"已是最新版本（{UpdateService.CurrentVersion}）。";
                UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "SuccessBrush");
            }
        }
        finally
        {
            CheckUpdateButton.IsEnabled = true;
        }
    }

    private void SavePrefix_Click(object sender, RoutedEventArgs e)
    {
        var prefix = UpdateService.NormalizePrefix(PrefixBox.Text);
        if (!Uri.TryCreate(prefix, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http"))
        {
            UpdateStatusText.Text = "加速前缀必须以 http:// 或 https:// 开头。";
            UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
            return;
        }
        GoalStore.Current.UpdateConfig(c => c.UpdatePrefix = prefix);
        PrefixBox.Text = prefix;
        UpdateStatusText.Text = "加速前缀已保存。";
        UpdateStatusText.SetResourceReference(TextBlock.ForegroundProperty, "SuccessBrush");
        (Window.GetWindow(this) as MainWindow)?.Toast("更新前缀已保存");
    }

    private void ResetPrefix_Click(object sender, RoutedEventArgs e)
    {
        PrefixBox.Text = "https://ghproxy.net/";
        SavePrefix_Click(sender, e);
    }
}
