using System.ComponentModel;
using System.Runtime.CompilerServices;
using System.Text.Json.Serialization;

namespace Goal.Models;

public sealed class UserProfile
{
    public string Name { get; set; } = "朋友";
    public string Motto { get; set; } = "把目标，做小一点。";
}

public sealed class AppConfig
{
    public string Theme { get; set; } = "sand";
    public string Accent { get; set; } = "#D9653B";
    public bool Dark { get; set; }
    public string UpdatePrefix { get; set; } = "https://ghproxy.net/";
}

public sealed class TaskProof
{
    public string Name { get; set; } = string.Empty;
    public string StoredName { get; set; } = string.Empty;

    /// <summary>image / video / audio / file</summary>
    public string Kind { get; set; } = "file";
    public long Size { get; set; }
    public DateTimeOffset AddedAt { get; set; } = DateTimeOffset.Now;
}

public sealed class AppData
{
    public string App { get; set; } = "goal";
    public int SchemaVersion { get; set; } = 1;
    public string AppVersion { get; set; } = "1.0.0";
    public string ExportedAt { get; set; } = DateTimeOffset.Now.ToString("O");
    public UserProfile User { get; set; } = new();
    public AppConfig Config { get; set; } = new();
    public List<TaskItem> Tasks { get; set; } = new();
}

public sealed class TaskItem : INotifyPropertyChanged
{
    private string _mode = "submit";
    private int _target;
    private int _progress;
    private string _unit = "次";
    private string _title = string.Empty;
    private string _note = string.Empty;
    private string? _dueDate;
    private bool _done;
    private DateTimeOffset? _completedAt;
    private DateTimeOffset? _timerStartedAt;
    private List<TaskProof> _proofs = new();

    public string Id { get; set; } = Guid.NewGuid().ToString("N");
    public string Title { get => _title; set => Set(ref _title, value); }
    public string Note { get => _note; set => Set(ref _note, value); }

    /// <summary>submit / duration / variation</summary>
    public string Mode { get => _mode; set { if (Set(ref _mode, value)) NotifyMode(); } }

    /// <summary>duration stores seconds; variation stores the target count.</summary>
    public int Target { get => _target; set { if (Set(ref _target, value)) NotifyMode(); } }

    /// <summary>duration stores accumulated seconds; variation stores current count.</summary>
    public int Progress { get => _progress; set { if (Set(ref _progress, value)) NotifyMode(); } }
    public string Unit { get => _unit; set => Set(ref _unit, value); }
    public string? DueDate { get => _dueDate; set { if (Set(ref _dueDate, value)) NotifyMode(); } }
    public bool Done { get => _done; set { if (Set(ref _done, value)) NotifyMode(); } }
    public DateTimeOffset? CompletedAt { get => _completedAt; set => Set(ref _completedAt, value); }
    public DateTimeOffset? TimerStartedAt { get => _timerStartedAt; set { if (Set(ref _timerStartedAt, value)) NotifyMode(); } }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.Now;

    /// <summary>Local proof files attached when a submit task is completed.</summary>
    public List<TaskProof> Proofs
    {
        get => _proofs;
        set
        {
            _proofs = value ?? new List<TaskProof>();
            NotifyProofs();
        }
    }

    [JsonIgnore] public bool IsSubmit => Mode == "submit";
    [JsonIgnore] public bool IsDuration => Mode == "duration";
    [JsonIgnore] public bool IsVariation => Mode == "variation";
    [JsonIgnore] public bool IsRunning => TimerStartedAt is not null && !Done;
    [JsonIgnore] public bool HasNote => !string.IsNullOrWhiteSpace(Note);
    [JsonIgnore] public bool HasProofs => Proofs.Count > 0;
    [JsonIgnore] public string ProofLabel => Proofs.Count == 0 ? "提交材料后完成" : $"{Proofs.Count} 份材料";
    [JsonIgnore] public string ProofSummary => Proofs.Count == 0 ? string.Empty : string.Join("、", Proofs.Select(p => p.Name));

    [JsonIgnore]
    public string ModeLabel => Mode switch
    {
        "duration" => "时长",
        "variation" => "变化",
        _ => "提交"
    };

    [JsonIgnore]
    public int EffectiveProgress
    {
        get
        {
            if (Done) return Target > 0 ? Target : Progress;
            if (IsDuration && IsRunning)
                return Math.Max(0, Progress + (int)Math.Max(0, (DateTimeOffset.Now - TimerStartedAt!.Value).TotalSeconds));
            return Progress;
        }
    }

    [JsonIgnore]
    public int Percent
    {
        get
        {
            if (Target <= 0) return Done ? 100 : 0;
            return Math.Clamp((int)Math.Round(EffectiveProgress * 100.0 / Target), 0, 100);
        }
    }

    [JsonIgnore]
    public string ProgressLabel
    {
        get
        {
            if (IsDuration)
            {
                var current = FormatClock(EffectiveProgress);
                var target = FormatClock(Target);
                return Done ? "时长已达成" : $"{current} / {target}";
            }
            if (IsVariation)
                return Done ? "变化已达成" : $"{EffectiveProgress} / {Target} {Unit}";
            return Done ? "已归档到完成" : ProofLabel;
        }
    }

    [JsonIgnore]
    public string StatusLine
    {
        get
        {
            if (Done) return CompletedAt is null ? "已完成" : $"完成于 {CompletedAt.Value.LocalDateTime:MM月dd日 HH:mm}";
            var due = DueLabel;
            return string.IsNullOrEmpty(due) ? "没有截止日期" : due;
        }
    }

    [JsonIgnore]
    public string DueLabel
    {
        get
        {
            if (string.IsNullOrWhiteSpace(DueDate) || !DateTime.TryParse(DueDate, out var due)) return string.Empty;
            var today = DateTime.Today;
            var days = (due.Date - today).Days;
            if (days == 0) return "今天到期";
            if (days == 1) return "明天到期";
            if (days > 1) return $"{days} 天后到期";
            if (days == -1) return "昨天已逾期";
            return $"逾期 {-days} 天";
        }
    }

    [JsonIgnore]
    public string DueTone => DueLabel.Contains("逾期") ? "danger" : "normal";

    public void StartTimer(DateTimeOffset now)
    {
        if (Done || !IsDuration || IsRunning) return;
        TimerStartedAt = now;
        OnPropertyChanged(nameof(StatusLine));
    }

    public int PauseTimer(DateTimeOffset now)
    {
        if (!IsRunning) return EffectiveProgress;
        var elapsed = EffectiveProgress;
        TimerStartedAt = null;
        Progress = Math.Max(Progress, elapsed);
        return Progress;
    }

    public void AddProofs(IEnumerable<TaskProof> proofs)
    {
        Proofs.AddRange(proofs);
        NotifyProofs();
    }

    public void NotifyProofs()
    {
        OnPropertyChanged(nameof(Proofs));
        OnPropertyChanged(nameof(HasProofs));
        OnPropertyChanged(nameof(ProofLabel));
        OnPropertyChanged(nameof(ProofSummary));
        OnPropertyChanged(nameof(ProgressLabel));
    }

    public void RefreshTime()
    {
        OnPropertyChanged(nameof(EffectiveProgress));
        OnPropertyChanged(nameof(Percent));
        OnPropertyChanged(nameof(ProgressLabel));
        OnPropertyChanged(nameof(StatusLine));
        OnPropertyChanged(nameof(IsRunning));
    }

    private static string FormatClock(int seconds)
    {
        if (seconds < 0) seconds = 0;
        if (seconds >= 3600)
        {
            var h = seconds / 3600;
            var m = seconds % 3600 / 60;
            return m == 0 ? $"{h} 小时" : $"{h} 小时 {m} 分";
        }
        return $"{seconds / 60:00}:{seconds % 60:00}";
    }

    private void NotifyMode()
    {
        OnPropertyChanged(nameof(IsSubmit));
        OnPropertyChanged(nameof(IsDuration));
        OnPropertyChanged(nameof(IsVariation));
        OnPropertyChanged(nameof(IsRunning));
        OnPropertyChanged(nameof(HasNote));
        OnPropertyChanged(nameof(HasProofs));
        OnPropertyChanged(nameof(ProofLabel));
        OnPropertyChanged(nameof(ProofSummary));
        OnPropertyChanged(nameof(ModeLabel));
        OnPropertyChanged(nameof(EffectiveProgress));
        OnPropertyChanged(nameof(Percent));
        OnPropertyChanged(nameof(ProgressLabel));
        OnPropertyChanged(nameof(StatusLine));
        OnPropertyChanged(nameof(DueLabel));
        OnPropertyChanged(nameof(DueTone));
    }

    public event PropertyChangedEventHandler? PropertyChanged;

    private bool Set<T>(ref T field, T value, [CallerMemberName] string? name = null)
    {
        if (EqualityComparer<T>.Default.Equals(field, value)) return false;
        field = value;
        OnPropertyChanged(name);
        return true;
    }

    private void OnPropertyChanged([CallerMemberName] string? name = null)
        => PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(name));
}
