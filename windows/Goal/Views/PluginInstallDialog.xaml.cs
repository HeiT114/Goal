using System.Windows;
using System.Windows.Controls;
using Goal.Models;

namespace Goal.Views;

public partial class PluginInstallDialog : UserControl
{
    public event Action? InstallRequested;
    public event Action? Dismissed;

    public PluginInstallDialog(PluginPackagePreview preview)
    {
        InitializeComponent();
        var manifest = preview.Manifest;
        InitialText.Text = string.IsNullOrWhiteSpace(manifest.Name) ? "P" : manifest.Name.Trim()[..1];
        PluginNameText.Text = manifest.Name;
        PluginMetaText.Text = $"v{manifest.Version}  ·  {manifest.Author}  ·  {manifest.Id}";
        EntryText.Text = manifest.Entry;
        NetworkText.Text = preview.NetworkLabel;
        DescriptionText.Text = string.IsNullOrWhiteSpace(preview.DescriptionText) ? "（说明文件是空的）" : preview.DescriptionText;
        Loaded += (_, _) => ReadCheck.Focus();
    }

    private void ReadCheck_Changed(object sender, RoutedEventArgs e)
    {
        if (InstallButton is not null) InstallButton.IsEnabled = ReadCheck.IsChecked == true;
    }

    private void Install_Click(object sender, RoutedEventArgs e)
    {
        if (ReadCheck.IsChecked != true) return;
        InstallRequested?.Invoke();
    }

    private void Cancel_Click(object sender, RoutedEventArgs e) => Dismissed?.Invoke();
}
