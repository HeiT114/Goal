using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using Goal.Models;
using Goal.Services;

namespace Goal.Views;

public partial class PluginRunnerDialog : UserControl
{
    private readonly InstalledPlugin _plugin;
    private readonly PluginPanel _panel;

    public PluginRunnerDialog(InstalledPlugin plugin, PluginPanel panel)
    {
        InitializeComponent();
        _plugin = plugin;
        _panel = panel;
        TitleText.Text = string.IsNullOrWhiteSpace(panel.Title) ? plugin.Name : panel.Title;
        SubtitleText.Text = panel.Subtitle;
        SubtitleText.Visibility = string.IsNullOrWhiteSpace(panel.Subtitle) ? Visibility.Collapsed : Visibility.Visible;
        MetaText.Text = $"{plugin.Author}  ·  v{plugin.Version}  ·  {plugin.NetworkLabel}";
        NoticeText.Text = $"面板由插件「{plugin.Name}」提供 · goal 只渲染声明式内容，不执行插件代码。";
        var accent = StorageService.NormalizeHex(panel.Accent);
        if (accent is not null) AccentStripe.Background = ThemeService.BrushFromHex(accent);
        RenderBlocks();
    }

    private void RenderBlocks()
    {
        BlocksHost.Children.Clear();
        if (_panel.Blocks.Count == 0)
        {
            var empty = new TextBlock
            {
                Text = "这个插件面板还没有内容。",
                Margin = new Thickness(0, 20, 0, 20),
                HorizontalAlignment = HorizontalAlignment.Center
            };
            empty.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
            BlocksHost.Children.Add(empty);
            return;
        }

        foreach (var block in _panel.Blocks)
        {
            switch (block.Type)
            {
                case "heading":
                    BlocksHost.Children.Add(new TextBlock
                    {
                        Text = block.Text ?? "",
                        Style = (Style)FindResource("H2Text"),
                        TextWrapping = TextWrapping.Wrap,
                        Margin = new Thickness(0, 6, 0, 8)
                    });
                    break;
                case "text":
                    BlocksHost.Children.Add(new TextBlock
                    {
                        Text = block.Text ?? "",
                        Style = (Style)FindResource("BodyText"),
                        TextWrapping = TextWrapping.Wrap,
                        Margin = new Thickness(0, 0, 0, 12)
                    });
                    break;
                case "quote":
                    BlocksHost.Children.Add(BuildQuote(block.Text ?? ""));
                    break;
                case "divider":
                    BlocksHost.Children.Add(new Border
                    {
                        Height = 1,
                        Background = (Brush)FindResource("LineBrush"),
                        Margin = new Thickness(0, 8, 0, 16)
                    });
                    break;
                case "link":
                    BlocksHost.Children.Add(BuildLink(block));
                    break;
                case "addTask":
                    BlocksHost.Children.Add(BuildAddTask(block));
                    break;
                case "checklist":
                    BlocksHost.Children.Add(BuildChecklist(block));
                    break;
            }
        }
    }

    private Border BuildQuote(string text)
    {
        var border = new Border
        {
            Background = (Brush)FindResource("Surface2Brush"),
            CornerRadius = new CornerRadius(11),
            Padding = new Thickness(14, 12, 14, 12),
            Margin = new Thickness(0, 2, 0, 14)
        };
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(3) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        var accent = new Border { Background = (Brush)FindResource("AccentBrush"), CornerRadius = new CornerRadius(2) };
        grid.Children.Add(accent);
        var body = new TextBlock
        {
            Text = text,
            TextWrapping = TextWrapping.Wrap,
            FontSize = 13,
            LineHeight = 22,
            Margin = new Thickness(12, 0, 0, 0)
        };
        body.SetResourceReference(TextBlock.ForegroundProperty, "InkBrush");
        Grid.SetColumn(body, 1);
        grid.Children.Add(body);
        border.Child = grid;
        return border;
    }

    private FrameworkElement BuildLink(PluginBlock block)
    {
        var panel = new StackPanel { Margin = new Thickness(0, 0, 0, 14) };
        var label = string.IsNullOrWhiteSpace(block.Label) ? block.Url ?? "打开链接" : block.Label;
        var button = new Button
        {
            Style = (Style)FindResource("GhostButton"),
            Content = "↗  " + label,
            HorizontalAlignment = HorizontalAlignment.Left
        };
        button.Click += async (_, _) =>
        {
            var main = Window.GetWindow(this) as MainWindow;
            if (main is null) return;
            if (string.IsNullOrWhiteSpace(block.Url))
            {
                await main.ShowMessageAsync("链接为空", "插件没有提供可打开的链接。");
                return;
            }
            if (!PluginService.Current.TryOpenExternalLink(_plugin, block.Url, out var error))
                await main.ShowMessageAsync("已阻止打开", error);
        };
        panel.Children.Add(button);
        return panel;
    }

    private Border BuildAddTask(PluginBlock block)
    {
        var mode = block.TaskMode is "duration" or "variation" ? block.TaskMode! : "submit";
        var title = !string.IsNullOrWhiteSpace(block.TaskTitle)
            ? block.TaskTitle!
            : !string.IsNullOrWhiteSpace(block.Title) ? block.Title! : "插件计划";
        var unit = string.IsNullOrWhiteSpace(block.Unit) ? "次" : block.Unit!;
        var valid = mode switch
        {
            "duration" => block.Target is >= 1 and <= 1440,
            "variation" => block.Target is >= 1 and <= 100000,
            _ => true
        };
        var detail = mode switch
        {
            "duration" => $"时长 · {block.Target} 分钟",
            "variation" => $"变化 · {block.Target} {unit}",
            _ => "提交 · 由你确认完成"
        };

        var card = new Border
        {
            Style = (Style)FindResource("FlatCardStyle"),
            Padding = new Thickness(15, 13, 15, 13),
            Margin = new Thickness(0, 0, 0, 12)
        };
        var grid = new Grid();
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        grid.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        var info = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        var titleText = new TextBlock { Text = title, FontWeight = FontWeights.SemiBold, FontSize = 13.5, TextWrapping = TextWrapping.Wrap };
        titleText.SetResourceReference(TextBlock.ForegroundProperty, "InkBrush");
        info.Children.Add(titleText);
        var detailText = new TextBlock { Text = detail, FontSize = 11.5, Margin = new Thickness(0, 4, 0, 0) };
        detailText.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        info.Children.Add(detailText);
        grid.Children.Add(info);

        var add = new Button
        {
            Style = (Style)FindResource("SoftButton"),
            Content = "添加到主页",
            Padding = new Thickness(13, 7, 13, 7),
            IsEnabled = valid,
            VerticalAlignment = VerticalAlignment.Center
        };
        add.Click += (_, _) =>
        {
            var main = Window.GetWindow(this) as MainWindow;
            var target = mode == "duration" ? block.Target * 60 : mode == "variation" ? block.Target : 0;
            GoalStore.Current.AddTask(title, $"由插件「{_plugin.Name}」添加。", mode, target, unit, null);
            main?.Toast($"已添加计划「{title}」");
        };
        Grid.SetColumn(add, 1);
        grid.Children.Add(add);
        card.Child = grid;
        return card;
    }

    private StackPanel BuildChecklist(PluginBlock block)
    {
        var panel = new StackPanel { Margin = new Thickness(0, 0, 0, 10) };
        if (!string.IsNullOrWhiteSpace(block.Title))
        {
            var heading = new TextBlock
            {
                Text = block.Title,
                FontSize = 11.5,
                FontWeight = FontWeights.SemiBold,
                Margin = new Thickness(0, 0, 0, 7)
            };
            heading.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
            panel.Children.Add(heading);
        }
        foreach (var item in block.Items ?? new List<string>())
        {
            var row = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 5) };
            var dot = new TextBlock
            {
                Text = "○",
                FontSize = 12,
                Width = 18,
                VerticalAlignment = VerticalAlignment.Top
            };
            dot.SetResourceReference(TextBlock.ForegroundProperty, "AccentBrush");
            row.Children.Add(dot);
            var text = new TextBlock
            {
                Text = item,
                FontSize = 12.5,
                TextWrapping = TextWrapping.Wrap,
                MaxWidth = 520
            };
            text.SetResourceReference(TextBlock.ForegroundProperty, "InkBrush");
            row.Children.Add(text);
            panel.Children.Add(row);
        }
        return panel;
    }

    private void Close_Click(object sender, RoutedEventArgs e)
        => (Window.GetWindow(this) as MainWindow)?.CloseOverlay();
}
