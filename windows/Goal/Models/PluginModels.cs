using System.Text.Json.Serialization;

namespace Goal.Models;

public sealed class PluginManifest
{
    public string Id { get; set; } = string.Empty;
    public string Name { get; set; } = string.Empty;
    public string Version { get; set; } = string.Empty;
    public string Author { get; set; } = string.Empty;
    public string Entry { get; set; } = string.Empty;
    public string Description { get; set; } = string.Empty;
    public List<string> Network { get; set; } = new();
    public string? Homepage { get; set; }
}

public sealed class PluginPanel
{
    public string Type { get; set; } = "goal-panel";
    public string Title { get; set; } = string.Empty;
    public string Subtitle { get; set; } = string.Empty;
    public string Accent { get; set; } = string.Empty;
    public List<PluginBlock> Blocks { get; set; } = new();
}

public sealed class PluginBlock
{
    public string Type { get; set; } = string.Empty;
    public string? Text { get; set; }
    public string? Title { get; set; }
    public string? Label { get; set; }
    public string? Url { get; set; }
    public string? TaskTitle { get; set; }
    public string? TaskMode { get; set; }
    public string? Unit { get; set; }
    public int Target { get; set; }
    public List<string>? Items { get; set; }
}

public sealed class InstalledPlugin
{
    public PluginManifest Manifest { get; set; } = new();
    public string DirectoryPath { get; set; } = string.Empty;
    public bool Enabled { get; set; }
    public DateTimeOffset InstalledAt { get; set; }
    [JsonIgnore] public string EntryPath => Path.Combine(DirectoryPath, Manifest.Entry);
    [JsonIgnore] public string DescriptionPath => Path.Combine(DirectoryPath, Manifest.Description);
    [JsonIgnore] public string Id => Manifest.Id;
    [JsonIgnore] public string Name => Manifest.Name;
    [JsonIgnore] public string Version => Manifest.Version;
    [JsonIgnore] public string Author => Manifest.Author;
    [JsonIgnore] public string NetworkLabel => Manifest.Network.Count == 0
        ? "不访问网络"
        : "声明网络：" + string.Join("、", Manifest.Network);
}

public sealed class PluginPackagePreview
{
    public string ZipPath { get; set; } = string.Empty;
    public PluginManifest Manifest { get; set; } = new();
    public string DescriptionText { get; set; } = string.Empty;
    [JsonIgnore] public string NetworkLabel => Manifest.Network.Count == 0
        ? "不声明任何网络访问"
        : string.Join("、", Manifest.Network);
}
