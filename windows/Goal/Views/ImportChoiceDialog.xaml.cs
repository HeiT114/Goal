using System.Windows;
using System.Windows.Controls;
using Goal.Models;
using Goal.Services;

namespace Goal.Views;

public partial class ImportChoiceDialog : UserControl
{
    public event Action<string>? Chosen;

    public ImportChoiceDialog(AppData data, string fileName)
    {
        InitializeComponent();
        FileText.Text = "来源：" + fileName;
        var active = data.Tasks.Count(t => !t.Done);
        var done = data.Tasks.Count - active;
        ProfileText.Text = $"用户：{data.User.Name}　·　{data.User.Motto}";
        TaskText.Text = $"计划：{data.Tasks.Count} 条（进行中 {active}，已完成 {done}）";
        ThemeText.Text = $"外观：{data.Config.Theme}　·　{(data.Config.Dark ? "深色" : "浅色")}　·　{data.Config.Accent}";
    }

    private void Overwrite_Click(object sender, RoutedEventArgs e) => Chosen?.Invoke("overwrite");
    private void Merge_Click(object sender, RoutedEventArgs e) => Chosen?.Invoke("merge");
    private void Cancel_Click(object sender, RoutedEventArgs e) => Chosen?.Invoke("cancel");
}
