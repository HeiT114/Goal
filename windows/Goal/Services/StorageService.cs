using System.Text.Json;
using System.Text.Json.Serialization;
using Goal.Models;

namespace Goal.Services;

public static class StorageService
{
    public static readonly JsonSerializerOptions JsonOptions = new()
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        PropertyNameCaseInsensitive = true,
        WriteIndented = true,
        DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping
    };

    public static string DataDirectory
    {
        get
        {
            var dir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "goal");
            Directory.CreateDirectory(dir);
            return dir;
        }
    }

    public static string DataFile => Path.Combine(DataDirectory, "data.json");

    /// <summary>Set when an existing local file could not be read, so the shell can explain the reset.</summary>
    public static string? LastLoadWarning { get; private set; }

    public static AppData Load()
    {
        LastLoadWarning = null;
        var hadFile = File.Exists(DataFile);
        try
        {
            if (hadFile)
            {
                var json = File.ReadAllText(DataFile);
                var data = JsonSerializer.Deserialize<AppData>(json, JsonOptions);
                if (data is not null) return ValidateOrThrow(data);
                LastLoadWarning = "本地数据文件为空，已载入示例计划；原文件已保留备份。";
            }
        }
        catch
        {
            // A broken local data file must not stop the app. Keep a copy for recovery.
            try
            {
                if (File.Exists(DataFile))
                    File.Copy(DataFile, Path.Combine(DataDirectory, $"data.broken.{DateTime.Now:yyyyMMddHHmmss}.json"), true);
            }
            catch { }
            if (hadFile)
                LastLoadWarning = "本地数据读取失败，已保留原文件备份并载入示例计划。";
        }
        return CreateSeed();
    }

    public static void Save(AppData data)
    {
        data.App = "goal";
        data.SchemaVersion = 1;
        data.ExportedAt = DateTimeOffset.Now.ToString("O");
        Directory.CreateDirectory(DataDirectory);
        var tmp = DataFile + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(data, JsonOptions));
        try
        {
            if (File.Exists(DataFile)) File.Copy(DataFile, DataFile + ".bak", true);
        }
        catch
        {
            // A failed backup should not stop the primary save.
        }
        File.Move(tmp, DataFile, true);
    }

    public static string ExportJson(AppData data)
    {
        var copy = CloneForExport(data);
        copy.App = "goal";
        copy.SchemaVersion = 1;
        copy.ExportedAt = DateTimeOffset.Now.ToString("O");
        return JsonSerializer.Serialize(copy, JsonOptions);
    }

    public static AppData ParseImport(string json)
    {
        if (string.IsNullOrWhiteSpace(json)) throw new InvalidDataException("文件是空的。");
        if (json.Length > 8 * 1024 * 1024) throw new InvalidDataException("文件过大（上限 8 MB）。");
        AppData? data;
        try
        {
            data = JsonSerializer.Deserialize<AppData>(json, JsonOptions);
        }
        catch (JsonException ex)
        {
            throw new InvalidDataException($"JSON 格式不正确：{ex.Message}");
        }
        if (data is null) throw new InvalidDataException("没有读取到有效数据。");
        return ValidateOrThrow(data);
    }

    public static AppData ValidateOrThrow(AppData data)
    {
        if (!string.Equals(data.App, "goal", StringComparison.OrdinalIgnoreCase))
            throw new InvalidDataException("这不是 goal 的备份文件（app 字段应为 goal）。");
        if (data.SchemaVersion < 1 || data.SchemaVersion > 1)
            throw new InvalidDataException($"暂不支持 schemaVersion {data.SchemaVersion}。");
        data.User ??= new UserProfile();
        data.Config ??= new AppConfig();
        data.Tasks ??= new List<TaskItem>();
        if (data.Tasks.Count > 5000) throw new InvalidDataException("任务数量超过 5000 条。");

        data.User.Name = string.IsNullOrWhiteSpace(data.User.Name) ? "朋友" : data.User.Name.Trim()[..Math.Min(24, data.User.Name.Trim().Length)];
        data.User.Motto = string.IsNullOrWhiteSpace(data.User.Motto) ? "把目标，做小一点。" : data.User.Motto.Trim()[..Math.Min(80, data.User.Motto.Trim().Length)];

        data.Config.Theme = string.IsNullOrWhiteSpace(data.Config.Theme) ? "sand" : data.Config.Theme.Trim();
        data.Config.Accent = NormalizeHex(data.Config.Accent) ?? "#D9653B";
        data.Config.UpdatePrefix = string.IsNullOrWhiteSpace(data.Config.UpdatePrefix)
            ? "https://ghproxy.net/"
            : data.Config.UpdatePrefix.Trim();
        if (!data.Config.UpdatePrefix.StartsWith("http://") && !data.Config.UpdatePrefix.StartsWith("https://"))
            throw new InvalidDataException("更新加速前缀必须是 http(s) 地址。");

        var ids = new HashSet<string>();
        foreach (var task in data.Tasks)
        {
            if (string.IsNullOrWhiteSpace(task.Id) || !ids.Add(task.Id))
            {
                task.Id = Guid.NewGuid().ToString("N");
                ids.Add(task.Id);
            }
            task.Title = string.IsNullOrWhiteSpace(task.Title) ? "未命名计划" : task.Title.Trim()[..Math.Min(80, task.Title.Trim().Length)];
            task.Note = (task.Note ?? string.Empty).Trim()[..Math.Min(500, (task.Note ?? string.Empty).Trim().Length)];
            task.Mode = task.Mode is "duration" or "variation" or "submit" ? task.Mode : "submit";
            if (task.IsDuration)
            {
                task.Target = Math.Clamp(task.Target, 1, 24 * 3600);
                task.Progress = Math.Clamp(task.Progress, 0, task.Target);
            }
            else if (task.IsVariation)
            {
                task.Target = Math.Clamp(task.Target, 1, 100000);
                task.Progress = Math.Clamp(task.Progress, 0, task.Target);
                task.Unit = string.IsNullOrWhiteSpace(task.Unit) ? "次" : task.Unit.Trim()[..Math.Min(8, task.Unit.Trim().Length)];
            }
            else
            {
                task.Unit = "次";
                task.Progress = 0;
                task.Target = 0;
            }
            if (!string.IsNullOrWhiteSpace(task.DueDate) && !DateTime.TryParse(task.DueDate, out _))
                task.DueDate = null;
            if (task.Done && task.CompletedAt is null) task.CompletedAt = DateTimeOffset.Now;
            if (!task.Done) task.CompletedAt = null;
            if (task.IsRunning && !task.IsDuration) task.TimerStartedAt = null;
            task.Proofs = (task.Proofs ?? new List<TaskProof>())
                .Where(p => p is not null && !string.IsNullOrWhiteSpace(p.Name) && !string.IsNullOrWhiteSpace(p.StoredName))
                .Take(ProofService.MaxProofs)
                .Select(p => new TaskProof
                {
                    Name = Clean(p.Name, 120),
                    StoredName = Clean(Path.GetFileName(p.StoredName), 160),
                    Kind = p.Kind is "image" or "video" or "audio" or "file" ? p.Kind : "file",
                    Size = Math.Max(0, p.Size),
                    AddedAt = p.AddedAt == default ? DateTimeOffset.Now : p.AddedAt
                })
                .ToList();
        }
        return data;
    }

    private static string Clean(string value, int max)
    {
        var text = (value ?? string.Empty).Trim();
        return text[..Math.Min(max, text.Length)];
    }

    public static string? NormalizeHex(string? input)
    {
        if (string.IsNullOrWhiteSpace(input)) return null;
        var hex = input.Trim();
        if (!hex.StartsWith('#')) hex = "#" + hex;
        if (hex.Length != 7 && hex.Length != 4) return null;
        for (var i = 1; i < hex.Length; i++)
            if (!Uri.IsHexDigit(hex[i])) return null;
        if (hex.Length == 4)
            hex = $"#{hex[1]}{hex[1]}{hex[2]}{hex[2]}{hex[3]}{hex[3]}";
        return hex.ToUpperInvariant();
    }

    public static AppData CreateSeed()
    {
        var data = new AppData();
        data.Tasks.Add(new TaskItem
        {
            Title = "给今天留出一段不被打扰的时间",
            Note = "25 分钟，从一件事开始。",
            Mode = "duration",
            Target = 25 * 60,
            CreatedAt = DateTimeOffset.Now
        });
        data.Tasks.Add(new TaskItem
        {
            Title = "写下今天最重要的一件事",
            Note = "做到后提交材料，才算完成。",
            Mode = "submit",
            DueDate = DateTime.Today.ToString("yyyy-MM-dd"),
            CreatedAt = DateTimeOffset.Now
        });
        data.Tasks.Add(new TaskItem
        {
            Title = "读完计划里的 10 页",
            Note = "数值达到目标后自动完成。",
            Mode = "variation",
            Target = 10,
            Progress = 0,
            Unit = "页",
            CreatedAt = DateTimeOffset.Now
        });
        return data;
    }

    public static AppData CloneForExport(AppData data)
    {
        var json = JsonSerializer.Serialize(data, JsonOptions);
        var clone = JsonSerializer.Deserialize<AppData>(json, JsonOptions) ?? new AppData();
        return clone;
    }
}
