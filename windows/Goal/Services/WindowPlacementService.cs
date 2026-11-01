using System.IO;
using System.Text.Json;

namespace Goal.Services;

public sealed class WindowPlacementState
{
    public double Left { get; set; }
    public double Top { get; set; }
    public double Width { get; set; }
    public double Height { get; set; }
    public bool Maximized { get; set; }
}

/// <summary>
/// Remembers the desktop window geometry in a small file next to the data file.
/// It is intentionally separate from data.json, which is shared with Android.
/// </summary>
public static class WindowPlacementService
{
    private static string FilePath => Path.Combine(StorageService.DataDirectory, "window.json");

    public static WindowPlacementState? Load()
    {
        try
        {
            if (!File.Exists(FilePath)) return null;
            var state = JsonSerializer.Deserialize<WindowPlacementState>(File.ReadAllText(FilePath));
            if (state is null || state.Width < 900 || state.Height < 600) return null;
            if (double.IsNaN(state.Left) || double.IsNaN(state.Top) ||
                double.IsInfinity(state.Left) || double.IsInfinity(state.Top)) return null;
            return state;
        }
        catch
        {
            return null;
        }
    }

    public static void Save(WindowPlacementState state)
    {
        try
        {
            Directory.CreateDirectory(StorageService.DataDirectory);
            var tmp = FilePath + ".tmp";
            File.WriteAllText(tmp, JsonSerializer.Serialize(state));
            File.Move(tmp, FilePath, true);
        }
        catch
        {
            // Geometry is a convenience; a failed write must not affect the data.
        }
    }
}
