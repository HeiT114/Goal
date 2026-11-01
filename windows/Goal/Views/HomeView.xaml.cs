using System.Globalization;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;
using Goal.Controls;
using Goal.Services;

namespace Goal.Views;

public partial class HomeView : UserControl
{
    private string _filter = "all";
    private bool _ready;

    public HomeView()
    {
        InitializeComponent();
        Loaded += OnLoaded;
        GoalStore.Current.Changed += OnStoreChanged;
    }

    private void OnLoaded(object sender, RoutedEventArgs e)
    {
        _ready = true;
        Refresh();
    }

    private void OnStoreChanged()
    {
        if (!IsLoaded) return;
        Refresh();
    }

    public void Refresh()
    {
        if (!_ready) return;
        var store = GoalStore.Current;
        var hour = DateTime.Now.Hour;
        var greeting = hour switch
        {
            < 5 => "夜深了",
            < 11 => "早上好",
            < 14 => "中午好",
            < 18 => "下午好",
            _ => "晚上好"
        };
        GreetingText.Text = $"{greeting}，{store.User.Name}";
        SubGreetingText.Text = store.User.Motto;
        DateText.Text = DateTime.Now.ToString("M月d日 dddd", new CultureInfo("zh-CN"));
        ActiveStat.Text = store.ActiveCount.ToString();
        DoneStat.Text = store.DoneCount.ToString();
        PercentStat.Text = store.Percent + "%";
        PercentRing.StrokeDashArray = new DoubleCollection { Math.Max(0.001, store.Percent / 25.0), 4 };

        var items = store.Tasks.Where(t => _filter switch
        {
            "active" => !t.Done,
            "done" => t.Done,
            _ => true
        }).ToList();

        TaskStack.Children.Clear();
        foreach (var task in items)
        {
            TaskStack.Children.Add(new TaskCard(task) { Margin = new Thickness(0, 0, 0, 12) });
        }

        EmptyState.Visibility = items.Count == 0 ? Visibility.Visible : Visibility.Collapsed;
        TaskStack.Visibility = items.Count == 0 ? Visibility.Collapsed : Visibility.Visible;
        ListHint.Text = items.Count == 0 ? "尚无计划" : $"共 {items.Count} 条计划";
        if (items.Count == 0)
        {
            EmptyTitle.Text = _filter switch
            {
                "done" => "还没有完成的计划",
                "active" => "暂时没有进行中的计划",
                _ => "这里还很安静"
            };
            EmptyBody.Text = _filter switch
            {
                "done" => "完成的计划会留在这里。",
                "active" => "安排一个新的小目标。",
                _ => "新建一个计划，从最小的一步开始。"
            };
        }
    }

    private void Filter_Checked(object sender, RoutedEventArgs e)
    {
        if (!_ready) return;
        _filter = (sender as RadioButton)?.Tag as string ?? "all";
        Refresh();
    }

    private void AddButton_Click(object sender, RoutedEventArgs e)
    {
        (Window.GetWindow(this) as MainWindow)?.ShowTaskEditor();
    }
}
