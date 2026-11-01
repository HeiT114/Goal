using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;

namespace Goal.Services;

/// <summary>
/// Keeps a single goal window per user session. A second launch activates the
/// existing window instead of opening another copy against the same data file.
/// </summary>
public static class SingleInstance
{
    private const string MutexName = "goal.app.6f3a1c2e-8b4d-4f6a-9c1e-2d7b5a0f9e31";
    private const int SW_RESTORE = 9;
    private const int SW_SHOW = 5;

    private static Mutex? _mutex;

    public static bool TryAcquire()
    {
        _mutex = new Mutex(initiallyOwned: true, name: MutexName, createdNew: out var createdNew);
        if (createdNew) return true;

        _mutex.Dispose();
        _mutex = null;
        ActivateExistingWindow();
        return false;
    }

    private static void ActivateExistingWindow()
    {
        try
        {
            var currentId = Environment.ProcessId;
            var name = Process.GetCurrentProcess().ProcessName;
            for (var attempt = 0; attempt < 15; attempt++)
            {
                foreach (var process in Process.GetProcessesByName(name))
                {
                    if (process.Id == currentId) continue;
                    var handle = process.MainWindowHandle;
                    if (handle == IntPtr.Zero)
                    {
                        process.Dispose();
                        continue;
                    }

                    ShowWindow(handle, SW_RESTORE);
                    ShowWindow(handle, SW_SHOW);
                    SetForegroundWindow(handle);
                    process.Dispose();
                    return;
                }
                Thread.Sleep(200);
            }
        }
        catch
        {
            // Activation is best-effort; never block a second launch from exiting.
        }
    }

    public static void Release()
    {
        if (_mutex is null) return;
        try { _mutex.ReleaseMutex(); } catch { }
        _mutex.Dispose();
        _mutex = null;
    }

    [DllImport("user32.dll")]
    private static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    private static extern bool SetForegroundWindow(IntPtr hWnd);
}
