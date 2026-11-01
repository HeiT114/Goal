using System.Diagnostics;
using System.IO.Compression;
using System.Text.Json;
using System.Text.RegularExpressions;
using Goal.Models;

namespace Goal.Services;

public sealed class PluginService
{
    public const string ResponsibilityNotice =
        "插件由第三方作者独立发布，与《goal》原作者无关；用户添加插件后，应自行判断插件内容及浏览信息是否合规，原作者不承担由此产生的任何责任。";

    private static readonly Regex IdPattern = new("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$", RegexOptions.Compiled);
    private static readonly Regex HostPattern = new(@"^(\*\.)?[A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?$", RegexOptions.Compiled);
    private readonly Dictionary<string, bool> _enabled = new(StringComparer.OrdinalIgnoreCase);

    public static PluginService Current { get; } = new();

    public string PluginRoot
    {
        get
        {
            var dir = Path.Combine(StorageService.DataDirectory, "plugins");
            Directory.CreateDirectory(dir);
            return dir;
        }
    }

    private string StateFile => Path.Combine(StorageService.DataDirectory, "plugins-state.json");

    public event Action? Changed;

    public IReadOnlyList<InstalledPlugin> LoadInstalled()
    {
        ReadState();
        var list = new List<InstalledPlugin>();
        foreach (var dir in Directory.EnumerateDirectories(PluginRoot))
        {
            try
            {
                var manifestPath = Path.Combine(dir, "manifest.json");
                if (!File.Exists(manifestPath)) continue;
                var manifest = JsonSerializer.Deserialize<PluginManifest>(File.ReadAllText(manifestPath), StorageService.JsonOptions);
                if (manifest is null) continue;
                NormalizeAndValidateManifest(manifest, Path.GetFileName(dir));
                var plugin = new InstalledPlugin
                {
                    Manifest = manifest,
                    DirectoryPath = dir,
                    Enabled = _enabled.TryGetValue(manifest.Id, out var enabled) && enabled,
                    InstalledAt = Directory.GetCreationTimeUtc(dir)
                };
                if (File.Exists(plugin.EntryPath) && File.Exists(plugin.DescriptionPath))
                    list.Add(plugin);
            }
            catch
            {
                // Invalid plugin folders are ignored, never executed.
            }
        }
        return list.OrderBy(p => p.Name, StringComparer.CurrentCultureIgnoreCase).ToList();
    }

    public PluginPackagePreview InspectZip(string zipPath)
    {
        using var archive = OpenArchive(zipPath);
        var (prefix, manifestEntry) = FindManifest(archive);
        if (manifestEntry is null) throw new InvalidDataException("插件包根目录缺少 manifest.json。");
        var manifest = ReadManifest(manifestEntry);
        NormalizeAndValidateManifest(manifest, prefix);
        EnsureEntryExists(archive, prefix, manifest.Entry, "入口文件");
        EnsureEntryExists(archive, prefix, manifest.Description, "说明文件");

        var descriptionEntry = FindEntry(archive, prefix, manifest.Description)!;
        var description = ReadText(descriptionEntry, 200_000);
        return new PluginPackagePreview
        {
            ZipPath = zipPath,
            Manifest = manifest,
            DescriptionText = description.Trim()
        };
    }

    public InstalledPlugin Install(PluginPackagePreview preview)
    {
        using var archive = OpenArchive(preview.ZipPath);
        var (prefix, manifestEntry) = FindManifest(archive);
        if (manifestEntry is null) throw new InvalidDataException("插件包根目录缺少 manifest.json。");
        var manifest = ReadManifest(manifestEntry);
        NormalizeAndValidateManifest(manifest, prefix);
        if (_enabled.ContainsKey(manifest.Id) || Directory.Exists(Path.Combine(PluginRoot, manifest.Id)))
            throw new InvalidDataException("已安装同 ID 的插件，请先卸载旧版本。");
        EnsureEntryExists(archive, prefix, manifest.Entry, "入口文件");
        EnsureEntryExists(archive, prefix, manifest.Description, "说明文件");
        ValidatePanelBytes(archive, prefix, manifest.Entry);

        var temp = Path.Combine(PluginRoot, ".install-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(temp);
        try
        {
            long total = 0;
            var count = 0;
            foreach (var entry in archive.Entries)
            {
                if (string.IsNullOrEmpty(entry.Name)) continue;
                count++;
                if (count > 300) throw new InvalidDataException("插件包文件数量过多。");
                total += entry.Length;
                if (total > 12 * 1024 * 1024) throw new InvalidDataException("插件包解压后超过 12 MB。");
                var relative = NormalizeRelative(prefix.Length > 0 && entry.FullName.StartsWith(prefix, StringComparison.Ordinal)
                    ? entry.FullName[prefix.Length..]
                    : entry.FullName);
                if (string.IsNullOrEmpty(relative)) continue;
                var target = Path.GetFullPath(Path.Combine(temp, relative));
                if (!target.StartsWith(Path.GetFullPath(temp) + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase))
                    throw new InvalidDataException("插件包包含不安全的路径。");
                Directory.CreateDirectory(Path.GetDirectoryName(target)!);
                using var input = entry.Open();
                using var output = File.Create(target);
                input.CopyTo(output);
            }

            // Confirm the extracted plugin matches the manifest we showed to the user.
            var extractedManifest = JsonSerializer.Deserialize<PluginManifest>(
                File.ReadAllText(Path.Combine(temp, "manifest.json")), StorageService.JsonOptions);
            if (extractedManifest is null || !string.Equals(extractedManifest.Id, manifest.Id, StringComparison.Ordinal))
                throw new InvalidDataException("插件解压后的清单不一致。");
            if (!File.Exists(Path.Combine(temp, manifest.Entry)) || !File.Exists(Path.Combine(temp, manifest.Description)))
                throw new InvalidDataException("插件缺少声明的入口或说明文件。");

            var destination = Path.Combine(PluginRoot, manifest.Id);
            Directory.Move(temp, destination);
            _enabled[manifest.Id] = false;
            SaveState();
            Changed?.Invoke();
            return new InstalledPlugin
            {
                Manifest = manifest,
                DirectoryPath = destination,
                Enabled = false,
                InstalledAt = DateTimeOffset.Now
            };
        }
        catch
        {
            try { if (Directory.Exists(temp)) Directory.Delete(temp, true); } catch { }
            throw;
        }
    }

    public void SetEnabled(InstalledPlugin plugin, bool enabled)
    {
        _enabled[plugin.Id] = enabled;
        plugin.Enabled = enabled;
        SaveState();
        Changed?.Invoke();
    }

    public void Uninstall(InstalledPlugin plugin)
    {
        if (Directory.Exists(plugin.DirectoryPath))
            Directory.Delete(plugin.DirectoryPath, true);
        _enabled.Remove(plugin.Id);
        SaveState();
        Changed?.Invoke();
    }

    public string ReadDescription(InstalledPlugin plugin)
    {
        try { return File.ReadAllText(plugin.DescriptionPath); }
        catch { return "（说明文件读取失败）"; }
    }

    public PluginPanel ReadPanel(InstalledPlugin plugin)
    {
        if (!plugin.Enabled) throw new InvalidOperationException("请先在插件页面启用此插件。");
        if (!File.Exists(plugin.EntryPath)) throw new FileNotFoundException("插件入口文件不存在。");
        var panel = JsonSerializer.Deserialize<PluginPanel>(File.ReadAllText(plugin.EntryPath), StorageService.JsonOptions)
                    ?? throw new InvalidDataException("插件入口不是有效的 JSON。");
        if (panel.Type != "goal-panel") throw new InvalidDataException("不支持的插件运行时，仅支持 goal-panel。");
        if (string.IsNullOrWhiteSpace(panel.Title)) panel.Title = plugin.Name;
        if (panel.Title.Length > 80) panel.Title = panel.Title[..80];
        if (panel.Subtitle.Length > 220) panel.Subtitle = panel.Subtitle[..220];
        if (panel.Blocks.Count > 60) throw new InvalidDataException("插件面板内容过多。");
        foreach (var block in panel.Blocks)
        {
            block.Type = (block.Type ?? string.Empty).Trim().ToLowerInvariant() switch
            {
                "addtask" => "addTask",
                var type => type
            };
            block.Text = Truncate(block.Text, 2000);
            block.Title = Truncate(block.Title, 120);
            block.Label = Truncate(block.Label, 80);
            block.Url = Truncate(block.Url, 2000);
            block.TaskTitle = Truncate(block.TaskTitle, 80);
            block.Unit = Truncate(block.Unit, 8);
            block.TaskMode = block.TaskMode is "submit" or "duration" or "variation" ? block.TaskMode : "submit";
            if (block.Items is not null && block.Items.Count > 30) block.Items = block.Items.Take(30).ToList();
        }
        return panel;
    }

    public bool TryOpenExternalLink(InstalledPlugin plugin, string url, out string error)
    {
        error = string.Empty;
        if (!plugin.Enabled)
        {
            error = "请先启用插件。";
            return false;
        }
        if (!Uri.TryCreate(url, UriKind.Absolute, out var uri) || (uri.Scheme != "https" && uri.Scheme != "http"))
        {
            error = "插件给出的链接不是有效的 http(s) 地址。";
            return false;
        }
        if (!IsHostAllowed(plugin.Manifest.Network, uri.Host))
        {
            error = $"插件清单未声明访问 {uri.Host} 的网络权限，已阻止打开。";
            return false;
        }
        try
        {
            Process.Start(new ProcessStartInfo(uri.AbsoluteUri) { UseShellExecute = true });
            return true;
        }
        catch (Exception ex)
        {
            error = "无法打开链接：" + ex.Message;
            return false;
        }
    }

    public static string? NormalizeHex(string? input) => StorageService.NormalizeHex(input);

    private static bool IsHostAllowed(IEnumerable<string> rules, string host)
    {
        host = host.ToLowerInvariant();
        foreach (var raw in rules)
        {
            var rule = (raw ?? string.Empty).Trim().ToLowerInvariant();
            if (rule.Length == 0) continue;
            if (rule.StartsWith("*."))
            {
                var suffix = rule[1..];
                if (host.EndsWith(suffix, StringComparison.Ordinal) || host == rule[2..]) return true;
            }
            else if (host == rule) return true;
        }
        return false;
    }

    private void ReadState()
    {
        _enabled.Clear();
        try
        {
            if (!File.Exists(StateFile)) return;
            var state = JsonSerializer.Deserialize<PluginStateFile>(File.ReadAllText(StateFile), StorageService.JsonOptions);
            if (state?.Plugins is null) return;
            foreach (var item in state.Plugins)
                if (!string.IsNullOrWhiteSpace(item.Id))
                    _enabled[item.Id] = item.Enabled;
        }
        catch { }
    }

    private void SaveState()
    {
        var state = new PluginStateFile
        {
            Plugins = _enabled.Select(kv => new PluginState { Id = kv.Key, Enabled = kv.Value }).ToList()
        };
        File.WriteAllText(StateFile, JsonSerializer.Serialize(state, StorageService.JsonOptions));
    }

    private static ZipArchive OpenArchive(string zipPath)
    {
        if (!File.Exists(zipPath)) throw new FileNotFoundException("找不到插件包。");
        if (new FileInfo(zipPath).Length > 15 * 1024 * 1024) throw new InvalidDataException("插件包超过 15 MB。");
        try { return ZipFile.OpenRead(zipPath); }
        catch (InvalidDataException) { throw new InvalidDataException("这不像是有效的 .zip 插件包。"); }
    }

    private static (string prefix, ZipArchiveEntry? manifest) FindManifest(ZipArchive archive)
    {
        var candidates = archive.Entries
            .Where(e => !string.IsNullOrEmpty(e.Name) &&
                        string.Equals(e.Name, "manifest.json", StringComparison.OrdinalIgnoreCase))
            .OrderBy(e => e.FullName.Count(c => c == '/'))
            .ThenBy(e => e.FullName.Length)
            .ToList();
        if (candidates.Count == 0) return (string.Empty, null);
        var entry = candidates[0];
        var slash = entry.FullName.LastIndexOf('/');
        var prefix = slash >= 0 ? entry.FullName[..(slash + 1)] : string.Empty;
        return (prefix, entry);
    }

    private static ZipArchiveEntry? FindEntry(ZipArchive archive, string prefix, string relativePath)
    {
        var expected = prefix + NormalizeRelative(relativePath);
        return archive.Entries.FirstOrDefault(e =>
            string.Equals(e.FullName.Replace('\\', '/'), expected, StringComparison.OrdinalIgnoreCase));
    }

    private static void EnsureEntryExists(ZipArchive archive, string prefix, string relativePath, string label)
    {
        if (FindEntry(archive, prefix, relativePath) is null)
            throw new InvalidDataException($"{label} {relativePath} 不存在。");
    }

    private static PluginManifest ReadManifest(ZipArchiveEntry entry)
    {
        var text = ReadText(entry, 100_000);
        PluginManifest? manifest;
        try
        {
            manifest = JsonSerializer.Deserialize<PluginManifest>(text, StorageService.JsonOptions);
        }
        catch (JsonException ex)
        {
            throw new InvalidDataException("manifest.json 格式不正确：" + ex.Message);
        }
        return manifest ?? throw new InvalidDataException("manifest.json 是空的。");
    }

    private static string ReadText(ZipArchiveEntry entry, long limit)
    {
        if (entry.Length > limit) throw new InvalidDataException("插件中的文本文件过大。");
        using var reader = new StreamReader(entry.Open(), System.Text.Encoding.UTF8, true);
        return reader.ReadToEnd();
    }

    private static string NormalizeRelative(string path)
    {
        var normalized = path.Replace('\\', '/').TrimStart('/');
        if (normalized.Length == 0) return string.Empty;
        var parts = normalized.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (parts.Any(p => p == ".." || p == ".")) throw new InvalidDataException("插件包含不安全的相对路径。");
        if (Path.IsPathRooted(normalized)) throw new InvalidDataException("插件包含绝对路径。");
        return string.Join('/', parts);
    }

    private static string? Truncate(string? input, int max)
    {
        if (input is null) return null;
        return input.Length <= max ? input : input[..max];
    }

    private static void NormalizeAndValidateManifest(PluginManifest manifest, string folderHint)
    {
        manifest.Id = (manifest.Id ?? string.Empty).Trim();
        manifest.Name = (manifest.Name ?? string.Empty).Trim();
        manifest.Version = (manifest.Version ?? string.Empty).Trim();
        manifest.Author = (manifest.Author ?? string.Empty).Trim();
        manifest.Entry = NormalizeRelative(manifest.Entry ?? string.Empty);
        manifest.Description = NormalizeRelative(manifest.Description ?? string.Empty);
        manifest.Network ??= new List<string>();
        if (!IdPattern.IsMatch(manifest.Id)) throw new InvalidDataException("插件 id 只能包含字母、数字、点、下划线或连字符，长度 3–64。");
        if (manifest.Name.Length is < 1 or > 40) throw new InvalidDataException("插件名称应为 1–40 个字符。");
        if (manifest.Version.Length is < 1 or > 24) throw new InvalidDataException("插件版本应为 1–24 个字符。");
        if (manifest.Author.Length is < 1 or > 40) throw new InvalidDataException("插件作者应为 1–40 个字符。");
        if (manifest.Entry.Length == 0 || !manifest.Entry.EndsWith(".json", StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("entry 必须指向插件包内的 .json 入口文件。");
        if (manifest.Description.Length == 0 ||
            !(manifest.Description.EndsWith(".md", StringComparison.OrdinalIgnoreCase) ||
              manifest.Description.EndsWith(".txt", StringComparison.OrdinalIgnoreCase)))
            throw new InvalidDataException("description 必须指向插件包内的 .md 或 .txt 说明文件。");
        if (manifest.Network.Count > 20) throw new InvalidDataException("网络权限声明最多 20 条。");
        manifest.Network = manifest.Network
            .Select(n => (n ?? string.Empty).Trim().ToLowerInvariant())
            .Where(n => n.Length > 0)
            .Distinct()
            .ToList();
        foreach (var host in manifest.Network)
            if (!HostPattern.IsMatch(host) || host.Length > 100)
                throw new InvalidDataException("network 中只能填写域名或 *.域名，不能填写 URL。");
        if (!string.IsNullOrWhiteSpace(manifest.Homepage) &&
            (!Uri.TryCreate(manifest.Homepage, UriKind.Absolute, out var home) || home.Scheme != "https"))
            manifest.Homepage = null;
        if (manifest.Id.Length == 0) throw new InvalidDataException("插件缺少 id。");
    }

    private static void ValidatePanelBytes(ZipArchive archive, string prefix, string entry)
    {
        var zipEntry = FindEntry(archive, prefix, entry) ?? throw new InvalidDataException("插件入口文件不存在。");
        if (zipEntry.Length > 300_000) throw new InvalidDataException("插件入口文件超过 300 KB。");
        var text = ReadText(zipEntry, 300_000);
        PluginPanel? panel;
        try { panel = JsonSerializer.Deserialize<PluginPanel>(text, StorageService.JsonOptions); }
        catch (JsonException ex) { throw new InvalidDataException("插件入口 JSON 格式不正确：" + ex.Message); }
        if (panel is null || panel.Type != "goal-panel") throw new InvalidDataException("插件入口必须是 goal-panel 类型。");
    }

    private sealed class PluginStateFile
    {
        public List<PluginState> Plugins { get; set; } = new();
    }

    private sealed class PluginState
    {
        public string Id { get; set; } = string.Empty;
        public bool Enabled { get; set; }
    }
}
