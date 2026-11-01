using System.Net.Http;
using System.Reflection;
using System.Text.Json;

namespace Goal.Services;

public sealed record UpdateResult(
    bool HasUpdate,
    string LatestVersion,
    string Notes,
    string DownloadUrl,
    string? Error = null);

public static class UpdateService
{
    /// <summary>
    /// A small, static manifest published with the project. The GitHub acceleration prefix
    /// is prepended to this URL for both checking and downloading. Replace this constant
    /// with your own repository after publishing.
    /// </summary>
    public const string ManifestPath = "https://raw.githubusercontent.com/heit-233/goal/main/update.json";

    public static string CurrentVersion =>
        Assembly.GetEntryAssembly()?.GetName().Version?.ToString(3) ?? "1.0.0";

    public static string NormalizePrefix(string value)
    {
        value = (value ?? string.Empty).Trim();
        if (value.Length == 0) return string.Empty;
        if (!value.StartsWith("http://") && !value.StartsWith("https://")) return value;
        return value.EndsWith('/') ? value : value + "/";
    }

    public static string BuildUrl(string prefix, string target)
    {
        prefix = NormalizePrefix(prefix);
        return prefix.Length == 0 ? target : prefix + target;
    }

    public static async Task<UpdateResult> CheckAsync(string platform, string prefix, CancellationToken ct = default)
    {
        try
        {
            using var http = new HttpClient { Timeout = TimeSpan.FromSeconds(15) };
            http.DefaultRequestHeaders.UserAgent.ParseAdd("goal/" + CurrentVersion);
            var url = BuildUrl(prefix, ManifestPath);
            var json = await http.GetStringAsync(url, ct);
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;
            JsonElement target;
            if (root.TryGetProperty(platform, out target) && target.ValueKind == JsonValueKind.Object)
            {
                // Use the platform-specific object.
            }
            else if (root.TryGetProperty("version", out _))
            {
                target = root;
            }
            else
            {
                return new UpdateResult(false, CurrentVersion, "", "", "更新清单中没有 " + platform + " 平台的信息。");
            }

            var latest = target.TryGetProperty("version", out var v) ? v.GetString()?.TrimStart('v') ?? "" : "";
            var notes = target.TryGetProperty("notes", out var n) ? n.GetString() ?? "" : "";
            var download = target.TryGetProperty("url", out var u) ? u.GetString() ?? "" : "";
            if (!Version.TryParse(latest, out var latestVersion))
                return new UpdateResult(false, CurrentVersion, notes, download, "更新清单中的版本号无效。");
            var current = Version.TryParse(CurrentVersion, out var currentVersion) ? currentVersion : new Version(1, 0, 0);
            return new UpdateResult(latestVersion > current, latest, notes, download);
        }
        catch (Exception ex) when (ex is not OperationCanceledException)
        {
            return new UpdateResult(false, CurrentVersion, "", "", "检查更新失败：" + FriendlyError(ex));
        }
    }

    public static async Task DownloadAsync(string prefix, string url, string destination, IProgress<double>? progress, CancellationToken ct = default)
    {
        using var http = new HttpClient { Timeout = TimeSpan.FromMinutes(10) };
        http.DefaultRequestHeaders.UserAgent.ParseAdd("goal/" + CurrentVersion);
        using var response = await http.GetAsync(BuildUrl(prefix, url), HttpCompletionOption.ResponseHeadersRead, ct);
        response.EnsureSuccessStatusCode();
        var total = response.Content.Headers.ContentLength ?? -1;
        await using var input = await response.Content.ReadAsStreamAsync(ct);
        await using var output = File.Create(destination);
        var buffer = new byte[81920];
        long read = 0;
        int n;
        while ((n = await input.ReadAsync(buffer, ct)) > 0)
        {
            await output.WriteAsync(buffer.AsMemory(0, n), ct);
            read += n;
            if (total > 0) progress?.Report((double)read / total);
        }
    }

    public static string DefaultDownloadName(string platform, string version)
        => platform == "android" ? $"goal-{version}.apk" : $"goal-{version}.exe";

    private static string FriendlyError(Exception ex)
        => ex switch
        {
            HttpRequestException => "无法连接更新服务器，请检查网络或加速前缀。",
            TaskCanceledException => "连接超时。",
            JsonException => "更新清单格式不正确。",
            _ => ex.Message
        };
}
