using System.Diagnostics;
using System.IO;
using Goal.Models;

namespace Goal.Services;

/// <summary>
/// Copies user-selected proof files into the local goal data directory.
/// Nothing is uploaded and the original file is never moved or deleted.
/// </summary>
public static class ProofService
{
    public const long MaxFileBytes = 200L * 1024 * 1024;
    public const int MaxProofs = 20;

    public static readonly string[] ImageExtensions = { ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic", ".heif" };
    public static readonly string[] VideoExtensions = { ".mp4", ".mov", ".avi", ".mkv", ".webm", ".m4v", ".wmv" };
    public static readonly string[] AudioExtensions = { ".mp3", ".wav", ".m4a", ".aac", ".flac", ".ogg", ".opus" };

    public static string ProofDirectory(string taskId) =>
        Path.Combine(StorageService.DataDirectory, "proofs", SafeSegment(taskId));

    public static IReadOnlyList<TaskProof> Copy(TaskItem task, IReadOnlyList<string> files)
    {
        if (files.Count == 0) throw new InvalidDataException("没有选择材料。");
        if (task.Proofs.Count + files.Count > MaxProofs)
            throw new InvalidDataException($"每个计划最多保存 {MaxProofs} 份材料。");

        var dir = ProofDirectory(task.Id);
        Directory.CreateDirectory(dir);
        var created = new List<string>();
        var result = new List<TaskProof>();
        try
        {
            foreach (var source in files)
            {
                var info = new FileInfo(source);
                if (!info.Exists) throw new InvalidDataException($"找不到文件：{info.Name}");
                if (info.Length > MaxFileBytes) throw new InvalidDataException($"「{info.Name}」超过 200 MB。");
                var display = string.IsNullOrWhiteSpace(info.Name) ? "材料" : info.Name;
                var stored = $"{DateTime.Now:yyyyMMddHHmmssfff}-{Guid.NewGuid():N}"[..24] + "-" + SafeFileName(display);
                var target = Path.Combine(dir, stored);
                File.Copy(source, target, true);
                created.Add(target);
                result.Add(new TaskProof
                {
                    Name = display.Length > 120 ? display[..120] : display,
                    StoredName = stored,
                    Kind = KindOf(display),
                    Size = info.Length,
                    AddedAt = DateTimeOffset.Now
                });
            }
            return result;
        }
        catch
        {
            foreach (var path in created)
            {
                try { File.Delete(path); } catch { }
            }
            throw;
        }
    }

    public static void DeleteForTask(string taskId)
    {
        try
        {
            var dir = ProofDirectory(taskId);
            if (Directory.Exists(dir)) Directory.Delete(dir, true);
        }
        catch { }
    }

    public static void OpenFolder(TaskItem task)
    {
        try
        {
            var dir = ProofDirectory(task.Id);
            Directory.CreateDirectory(dir);
            Process.Start(new ProcessStartInfo("explorer.exe", $"\"{dir}\"") { UseShellExecute = true });
        }
        catch { }
    }

    public static string KindOf(string name)
    {
        var ext = Path.GetExtension(name).ToLowerInvariant();
        if (ImageExtensions.Contains(ext)) return "image";
        if (VideoExtensions.Contains(ext)) return "video";
        if (AudioExtensions.Contains(ext)) return "audio";
        return "file";
    }

    public static string KindLabel(string kind) => kind switch
    {
        "image" => "图片",
        "video" => "视频",
        "audio" => "音频",
        _ => "文件"
    };

    private static string SafeSegment(string value)
    {
        var safe = new string((value ?? string.Empty).Where(c => char.IsLetterOrDigit(c) || c is '-' or '_').ToArray());
        return string.IsNullOrEmpty(safe) ? "task" : safe[..Math.Min(64, safe.Length)];
    }

    private static string SafeFileName(string value)
    {
        var name = Path.GetFileName(value);
        foreach (var c in Path.GetInvalidFileNameChars()) name = name.Replace(c, '_');
        if (string.IsNullOrWhiteSpace(name)) name = "material";
        if (name.Length > 90)
        {
            var ext = Path.GetExtension(name);
            name = name[..Math.Min(70, name.Length)] + ext;
        }
        return name;
    }
}
