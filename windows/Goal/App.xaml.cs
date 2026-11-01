using System.Windows;
using System.Windows.Threading;
using Goal.Services;

namespace Goal;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        DispatcherUnhandledException += OnUnhandled;

        if (!SingleInstance.TryAcquire())
        {
            Shutdown(0);
            return;
        }

        GoalStore.Current.Load();
        ThemeService.Apply(GoalStore.Current.Config);
        var main = new MainWindow();
        MainWindow = main;
        main.Show();
    }

    protected override void OnExit(ExitEventArgs e)
    {
        SingleInstance.Release();
        base.OnExit(e);
    }

    private void OnUnhandled(object sender, DispatcherUnhandledExceptionEventArgs e)
    {
        MessageBox.Show("goal 遇到了一点问题：\n\n" + e.Exception.Message, "goal", MessageBoxButton.OK, MessageBoxImage.Warning);
        e.Handled = true;
    }
}
