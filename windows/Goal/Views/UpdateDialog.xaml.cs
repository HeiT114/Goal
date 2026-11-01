using System.Diagnostics;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using Goal.Services;

namespace Goal.Views;

public partial class UpdateDialog : UserControl
{
    private readonly UpdateResult _result;
    private readonly string _prefix;
    private CancellationTokenSource? _cts;
    private string? _downloadedPath;

    public UpdateDialog(UpdateResult result, string prefix)
    {
        InitializeComponent();
        _result = result;
        _prefix = prefix;
        CurrentVersionText.Text = UpdateService.CurrentVersion;
        LatestVersionText.Text = result.LatestVersion;
        NotesText.Text = string.IsNullOrWhiteSpace(result.Notes) ? "本次更新没有附加说明。" : result.Notes;
        if (string.IsNullOrWhiteSpace(result.DownloadUrl))
        {
            DownloadButton.IsEnabled = false;
            StatusText.Text = "更新清单没有提供下载地址。";
            StatusText.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
        }
    }

    private async void Download_Click(object sender, RoutedEventArgs e)
    {
        if (_downloadedPath is not null)
        {
            RevealDownload();
            return;
        }
        if (string.IsNullOrWhiteSpace(_result.DownloadUrl)) return;

        var downloads = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.UserProfile), "Downloads");
        Directory.CreateDirectory(downloads);
        var name = Path.GetFileNameWithoutExtension(UpdateService.DefaultDownloadName("windows", _result.LatestVersion));
        var destination = Path.Combine(downloads, $"{name}-{DateTime.Now:HHmmss}.exe");
        var temp = destination + ".part";
        _cts = new CancellationTokenSource();
        DownloadButton.IsEnabled = false;
        DownloadProgress.Visibility = Visibility.Visible;
        DownloadProgress.Value = 0;
        StatusText.Text = "正在下载更新…";
        StatusText.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        var progress = new Progress<double>(p =>
        {
            DownloadProgress.Value = Math.Clamp(p * 100, 0, 100);
            StatusText.Text = $"正在下载更新… {p * 100:0}%";
        });
        try
        {
            await UpdateService.DownloadAsync(_prefix, _result.DownloadUrl, temp, progress, _cts.Token);
            File.Move(temp, destination, true);
            _downloadedPath = destination;
            DownloadProgress.Value = 100;
            StatusText.Text = "下载完成：" + destination;
            StatusText.SetResourceReference(TextBlock.ForegroundProperty, "SuccessBrush");
            RevealButton.Visibility = Visibility.Visible;
            DownloadButton.Content = "重新下载";
        }
        catch (OperationCanceledException)
        {
            StatusText.Text = "已取消下载。";
            StatusText.SetResourceReference(TextBlock.ForegroundProperty, "MutedBrush");
        }
        catch (Exception ex)
        {
            StatusText.Text = "下载失败：" + ex.Message;
            StatusText.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
        }
        finally
        {
            DownloadButton.IsEnabled = true;
            DownloadProgress.Visibility = Visibility.Collapsed;
            try { if (File.Exists(temp)) File.Delete(temp); } catch { }
        }
    }

    private void Reveal_Click(object sender, RoutedEventArgs e) => RevealDownload();

    private void RevealDownload()
    {
        if (_downloadedPath is null || !File.Exists(_downloadedPath)) return;
        try
        {
            Process.Start(new ProcessStartInfo("explorer.exe", "/select,\"" + _downloadedPath + "\"") { UseShellExecute = true });
        }
        catch (Exception ex)
        {
            StatusText.Text = "无法打开文件夹：" + ex.Message;
            StatusText.SetResourceReference(TextBlock.ForegroundProperty, "DangerBrush");
        }
    }

    private void Close_Click(object sender, RoutedEventArgs e)
    {
        _cts?.Cancel();
        (Window.GetWindow(this) as MainWindow)?.CloseOverlay();
    }
}
