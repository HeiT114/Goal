using System.Diagnostics;
using System.IO;
using System.Text.RegularExpressions;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Goal.Models;
using Goal.Services;
using Microsoft.Win32;

namespace Goal.Views;

public partial class PluginsView : UserControl
{
    private bool _subscribed;
    private bool _ready;

    public PluginsView()
    {
        InitializeComponent();
        Loaded += (_, _) =>
        {
            _ready = true;
            if (!_subscribed)
            {
                PluginService.Current.Changed += OnPluginsChanged;
                _subscribed = true;
            }
            Refresh();
        };
        Unloaded += (_, _) =>
        {
            if (!_subscribed) return;
            PluginService.Current.Changed -= OnPluginsChanged;
            _subscribed = false;
        };
    }

    private void OnPluginsChanged()
    {
        if (IsLoaded) Refresh();
    }

    public void Refresh()
    {
        if (!_ready) return;
        var plugins = PluginService.Current.LoadInstalled();
        PluginCountText.Text = $"{plugins.Count} 个";
        EmptyState.Visibility = plugins.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        PluginStack.Children.Clear();
        foreach (var plugin in plugins)
            PluginStack.Children.Add(BuildPluginCard(plugin));
    }

    private Border BuildPluginCard(InstalledPlugin plugin)
    {
        var card = new Border
        {
            Style = (Style)FindResource("FlatCardStyle"),
            Padding = new Thickness(20, 17, 20, 17),
            Margin = new Thickness(0, 0, 0, 12)
        };
        var root = new Grid();
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var top = new Grid();
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        top.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });

        var icon = new Border
        {
            Width = 46,
            Height = 46,
            CornerRadius = new CornerRadius(14),
            Background = (Brush)FindResource("Surface2Brush"),
            VerticalAlignment = VerticalAlignment.Top
        };
        var letter = new TextBlock
        {
            Text = string.IsNullOrWhiteSpace(plugin.Name) ? "P" : plugin.Name.Trim()[..1],
            FontSize = 19,
            FontWeight = FontWeights.SemiBold,
            HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center
        };
        letter.SetResourceReference(TextBlock.ForegroundProperty, "AccentDeepBrush");
        icon.Child = letter;
        top.Children.Add(icon);

        var details = new StackPanel { Margin = new Thickness(14, 0, 14, 0), VerticalAlignment = VerticalAlignment.Center };
        var name = new TextBlock
        {
            Text = plugin.Name,
            FontSize = 15,
            FontWeight = FontWeights.SemiBold,
            TextTrimming = TextTrimming.CharacterEllipsis
        };
        name.SetResourceReference(TextBlock.ForegroundProperty, "InkBrush");
        details.Children.Add(name);
        var meta = new TextBlock
        {
            Text = $"v{plugin.Version}  ·  {plugin.Author}  ·  {plugin.NetworkLabel}",
            FontSize = 11.5,
            Margin = new Thickness(0, 4, 0, 0)
        };
        meta.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        details.Children.Add(meta);
        Grid.SetColumn(details, 1);
        top.Children.Add(details);

        var status = new Border
        {
            CornerRadius = new CornerRadius(7),
            Padding = new Thickness(9, 4, 9, 4),
            Background = (Brush)FindResource(plugin.Enabled ? "SuccessBrush" : "Surface2Brush"),
            VerticalAlignment = VerticalAlignment.Center
        };
        var statusText = new TextBlock
        {
            Text = plugin.Enabled ? "已启用" : "未启用",
            FontSize = 10.5,
            Foreground = plugin.Enabled ? Brushes.White : (Brush)FindResource("MutedBrush")
        };
        status.Child = statusText;
        Grid.SetColumn(status, 2);
        top.Children.Add(status);
        root.Children.Add(top);

        var bottom = new Grid { Margin = new Thickness(0, 13, 0, 0) };
        bottom.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        bottom.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var summary = new TextBlock
        {
            Text = BuildSummary(PluginService.Current.ReadDescription(plugin)),
            FontSize = 12,
            TextWrapping = TextWrapping.Wrap,
            LineHeight = 19,
            MaxHeight = 40,
            Margin = new Thickness(0, 0, 18, 0),
            VerticalAlignment = VerticalAlignment.Center
        };
        summary.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        bottom.Children.Add(summary);

        var actions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };
        if (plugin.Enabled)
        {
            var open = new Button { Style = (Style)FindResource("PrimaryButton"), Content = "打开", Padding = new Thickness(14, 7, 14, 7) };
            open.Click += (_, _) => (Window.GetWindow(this) as MainWindow)?.ShowPluginRunner(plugin);
            actions.Children.Add(open);
            var disable = new Button { Style = (Style)FindResource("GhostButton"), Content = "停用", Padding = new Thickness(12, 7, 12, 7), Margin = new Thickness(7, 0, 0, 0) };
            disable.Click += (_, _) =>
            {
                PluginService.Current.SetEnabled(plugin, false);
                (Window.GetWindow(this) as MainWindow)?.Toast($"已停用「{plugin.Name}」");
            };
            actions.Children.Add(disable);
        }
        else
        {
            var enable = new Button { Style = (Style)FindResource("PrimaryButton"), Content = "启用", Padding = new Thickness(14, 7, 14, 7) };
            enable.Click += async (_, _) =>
            {
                var main = Window.GetWindow(this) as MainWindow;
                if (main is null) return;
                var ok = await main.ConfirmAsync("启用插件",
                    $"即将启用「{plugin.Name}」。\n\n网络权限：{plugin.NetworkLabel}\n\n{PluginService.ResponsibilityNotice}",
                    "我已了解，启用");
                if (!ok) return;
                PluginService.Current.SetEnabled(plugin, true);
                main.Toast($"已启用「{plugin.Name}」");
            };
            actions.Children.Add(enable);
        }
        var uninstall = new Button { Style = (Style)FindResource("DangerTextButton"), Content = "卸载", Margin = new Thickness(5, 0, 0, 0) };
        uninstall.Click += async (_, _) =>
        {
            var main = Window.GetWindow(this) as MainWindow;
            if (main is null) return;
            if (!await main.ConfirmAsync("卸载插件", $"「{plugin.Name}」的插件文件将从本机删除，无法撤销。", "卸载", danger: true)) return;
            try
            {
                PluginService.Current.Uninstall(plugin);
                main.Toast($"已卸载「{plugin.Name}」");
            }
            catch (Exception ex)
            {
                await main.ShowMessageAsync("卸载失败", ex.Message);
            }
        };
        actions.Children.Add(uninstall);
        Grid.SetColumn(actions, 1);
        bottom.Children.Add(actions);
        Grid.SetRow(bottom, 1);
        root.Children.Add(bottom);
        card.Child = root;
        return card;
    }

    private static string BuildSummary(string text)
    {
        if (string.IsNullOrWhiteSpace(text)) return "（没有说明文件）";
        var cleaned = Regex.Replace(text, @"[#>*_\[\]]", " ");
        cleaned = Regex.Replace(cleaned, @"\s+", " ").Trim();
        if (cleaned.Length > 150) cleaned = cleaned[..150] + "…";
        return cleaned;
    }

    private async void AddPlugin_Click(object sender, RoutedEventArgs e)
    {
        var main = Window.GetWindow(this) as MainWindow;
        if (main is null) return;
        var dialog = new OpenFileDialog
        {
            Title = "选择 goal 插件包",
            Filter = "goal 插件包 (*.zip)|*.zip",
            CheckFileExists = true
        };
        if (dialog.ShowDialog(main) != true) return;
        try
        {
            var preview = PluginService.Current.InspectZip(dialog.FileName);
            main.ShowPluginInstallDialog(preview);
        }
        catch (Exception ex)
        {
            await main.ShowMessageAsync("无法读取插件包", ex.Message);
        }
    }

    private void OpenFolder_Click(object sender, RoutedEventArgs e)
    {
        try
        {
            var dir = PluginService.Current.PluginRoot;
            Process.Start(new ProcessStartInfo("explorer.exe", dir) { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            _ = (Window.GetWindow(this) as MainWindow)?.ShowMessageAsync("无法打开目录", ex.Message);
        }
    }
}
