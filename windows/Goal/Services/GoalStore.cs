using System.Collections.ObjectModel;
using System.ComponentModel;
using Goal.Models;

namespace Goal.Services;

public sealed class GoalStore
{
    public static GoalStore Current { get; } = new();

    private readonly ObservableCollection<TaskItem> _tasks = new();
    private DateTimeOffset _lastAutoSave = DateTimeOffset.MinValue;
    private bool _loading;
    private bool _saveErrorShown;

    public AppData Data { get; private set; } = new();
    public UserProfile User => Data.User;
    public AppConfig Config => Data.Config;
    public ObservableCollection<TaskItem> Tasks => _tasks;

    public event Action? Changed;
    public event Action<TaskItem, string>? TaskCompleted;
    public event Action<string>? Notice;

    private GoalStore() { }

    public void Load()
    {
        _loading = true;
        Data = StorageService.Load();
        _tasks.Clear();
        foreach (var task in Data.Tasks.OrderBy(t => t.Done).ThenBy(t => string.IsNullOrEmpty(t.DueDate) ? 1 : 0).ThenBy(t => t.DueDate).ThenByDescending(t => t.CreatedAt))
            _tasks.Add(task);
        _loading = false;
        Changed?.Invoke();
    }

    public TaskItem AddTask(string title, string note, string mode, int target, string unit, string? dueDate)
    {
        var task = new TaskItem
        {
            Title = title.Trim(),
            Note = note.Trim(),
            Mode = mode,
            Target = target,
            Unit = string.IsNullOrWhiteSpace(unit) ? "次" : unit.Trim(),
            DueDate = string.IsNullOrWhiteSpace(dueDate) ? null : dueDate,
            CreatedAt = DateTimeOffset.Now
        };
        _tasks.Insert(0, task);
        Persist();
        Changed?.Invoke();
        return task;
    }

    public void UpdateTask(TaskItem task, string title, string note, string mode, int target, string unit, string? dueDate)
    {
        task.Title = title.Trim();
        task.Note = note.Trim();
        task.Mode = mode;
        if (mode == "duration")
        {
            task.Target = Math.Clamp(target, 1, 24 * 3600);
            task.Progress = Math.Min(task.Progress, task.Target);
            task.Unit = "次";
        }
        else if (mode == "variation")
        {
            task.Target = Math.Clamp(target, 1, 100000);
            task.Progress = Math.Min(task.Progress, task.Target);
            task.Unit = string.IsNullOrWhiteSpace(unit) ? "次" : unit.Trim();
        }
        else
        {
            task.Target = 0;
            task.Progress = 0;
            task.TimerStartedAt = null;
            task.Unit = "次";
        }
        task.DueDate = string.IsNullOrWhiteSpace(dueDate) ? null : dueDate;
        if (task.Progress >= task.Target && task.Target > 0 && !task.Done)
            Complete(task, task.ModeLabel + "达成");
        Persist();
        Changed?.Invoke();
    }

    public void DeleteTask(TaskItem task)
    {
        ProofService.DeleteForTask(task.Id);
        _tasks.Remove(task);
        Persist();
        Changed?.Invoke();
    }

    public void ToggleDone(TaskItem task)
    {
        if (task.Done) Reopen(task);
        else Complete(task, "手动完成");
    }

    public void AddProofs(TaskItem task, IReadOnlyList<TaskProof> proofs)
    {
        if (proofs.Count == 0) return;
        task.AddProofs(proofs);
        Persist();
        Changed?.Invoke();
    }

    public void Complete(TaskItem task, string via)
    {
        if (task.Done) return;
        if (task.IsSubmit && via is "提交" or "手动完成" && !task.HasProofs)
        {
            Notice?.Invoke("请先选择文件、视频、音频或图片，再提交完成。");
            return;
        }
        if (task.IsDuration)
        {
            var elapsed = task.EffectiveProgress;
            task.TimerStartedAt = null;
            task.Progress = Math.Max(elapsed, task.Target);
        }
        else if (task.IsVariation)
        {
            task.Progress = Math.Max(task.Progress, task.Target);
        }
        task.Done = true;
        task.CompletedAt = DateTimeOffset.Now;
        Reorder();
        Persist();
        TaskCompleted?.Invoke(task, via);
        Changed?.Invoke();
    }

    public void Reopen(TaskItem task)
    {
        if (!task.Done) return;
        task.Done = false;
        task.CompletedAt = null;
        if (task.IsDuration) task.Progress = Math.Min(task.Progress, Math.Max(0, task.Target - 1));
        if (task.IsVariation) task.Progress = Math.Min(task.Progress, Math.Max(0, task.Target - 1));
        Reorder();
        Persist();
        Changed?.Invoke();
    }

    public void StartTimer(TaskItem task)
    {
        if (task.Done || !task.IsDuration) return;
        task.StartTimer(DateTimeOffset.Now);
        Persist();
        Changed?.Invoke();
    }

    public void PauseTimer(TaskItem task)
    {
        if (!task.IsDuration) return;
        var elapsed = task.PauseTimer(DateTimeOffset.Now);
        if (elapsed >= task.Target) Complete(task, "时长达成");
        Persist();
        Changed?.Invoke();
    }

    public void Increment(TaskItem task, int delta)
    {
        if (task.Done || !task.IsVariation) return;
        task.Progress = Math.Clamp(task.Progress + delta, 0, task.Target);
        if (task.Progress >= task.Target)
            Complete(task, "变化达成");
        else
        {
            Persist();
            Changed?.Invoke();
        }
    }

    public void Tick()
    {
        var completedAny = false;
        var timerRunning = false;
        foreach (var task in _tasks)
        {
            if (!task.IsDuration || !task.IsRunning) continue;
            timerRunning = true;
            task.RefreshTime();
            if (task.EffectiveProgress >= task.Target)
            {
                Complete(task, "时长达成");
                completedAny = true;
            }
        }

        // Only a running timer changes data on its own. Without this guard the app
        // rewrote data.json every 12 seconds while merely sitting open.
        if (timerRunning && !completedAny && (DateTimeOffset.Now - _lastAutoSave).TotalSeconds >= 12)
            Persist();
    }

    public void UpdateUser(string name, string motto)
    {
        Data.User.Name = string.IsNullOrWhiteSpace(name) ? "朋友" : name.Trim();
        Data.User.Motto = string.IsNullOrWhiteSpace(motto) ? "把目标，做小一点。" : motto.Trim();
        Persist();
        Changed?.Invoke();
    }

    public void UpdateConfig(Action<AppConfig> change)
    {
        change(Data.Config);
        Data.Config.Accent = StorageService.NormalizeHex(Data.Config.Accent) ?? "#D9653B";
        Persist();
        Changed?.Invoke();
    }

    public void Import(AppData imported, bool merge)
    {
        if (!merge)
        {
            Data.User = imported.User;
            Data.Config = imported.Config;
            Data.Tasks = imported.Tasks;
            ReplaceTasks(imported.Tasks);
        }
        else
        {
            var existing = new HashSet<string>(_tasks.Select(t => t.Id));
            var added = 0;
            foreach (var task in imported.Tasks)
            {
                if (existing.Contains(task.Id)) continue;
                existing.Add(task.Id);
                Data.Tasks.Add(task);
                _tasks.Add(task);
                added++;
            }
            Notice?.Invoke($"合并完成，新增 {added} 条计划。");
        }
        Reorder();
        Persist();
        Changed?.Invoke();
    }

    public int ActiveCount => _tasks.Count(t => !t.Done);
    public int DoneCount => _tasks.Count(t => t.Done);
    public int TotalCount => _tasks.Count;
    public int Percent => TotalCount == 0 ? 0 : (int)Math.Round(DoneCount * 100.0 / TotalCount);

    public void Persist()
    {
        if (_loading) return;
        Data.Tasks = _tasks.ToList();
        try
        {
            StorageService.Save(Data);
            _lastAutoSave = DateTimeOffset.Now;
            _saveErrorShown = false;
        }
        catch
        {
            if (_saveErrorShown) return;
            _saveErrorShown = true;
            Notice?.Invoke("保存失败：请检查磁盘空间，或 %APPDATA%\\goal 的写入权限。");
        }
    }

    private void ReplaceTasks(IEnumerable<TaskItem> tasks)
    {
        _tasks.Clear();
        foreach (var task in tasks) _tasks.Add(task);
        Reorder();
    }

    private void Reorder()
    {
        var ordered = _tasks.OrderBy(t => t.Done)
            .ThenBy(t => string.IsNullOrEmpty(t.DueDate) ? 1 : 0)
            .ThenBy(t => t.DueDate)
            .ThenByDescending(t => t.CreatedAt)
            .ToList();
        _tasks.Clear();
        foreach (var task in ordered) _tasks.Add(task);
        Data.Tasks = ordered;
    }
}
