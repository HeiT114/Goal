using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;

namespace Goal.Views;

public partial class ConfirmDialog : UserControl
{
    public event Action<bool>? Answered;

    public ConfirmDialog(string title, string body, string primaryText, string? cancelText = "取消", bool danger = false)
    {
        InitializeComponent();
        TitleText.Text = title;
        BodyText.Text = body;
        PrimaryButton.Content = primaryText;
        if (danger)
        {
            PrimaryButton.Background = (Brush)FindResource("DangerBrush");
            AccentRule.Background = (Brush)FindResource("DangerBrush");
        }
        if (string.IsNullOrEmpty(cancelText))
        {
            CancelButton.Visibility = Visibility.Collapsed;
            CancelButton.Content = "";
        }
        else
        {
            CancelButton.Content = cancelText;
        }
        Loaded += (_, _) =>
        {
            Opacity = 0;
            var transform = new ScaleTransform(0.97, 0.97);
            RenderTransform = transform;
            RenderTransformOrigin = new Point(0.5, 0.5);
            var ease = new System.Windows.Media.Animation.CubicEase { EasingMode = System.Windows.Media.Animation.EasingMode.EaseOut };
            BeginAnimation(OpacityProperty, new System.Windows.Media.Animation.DoubleAnimation(1, TimeSpan.FromMilliseconds(180)) { EasingFunction = ease });
            transform.BeginAnimation(ScaleTransform.ScaleXProperty, new System.Windows.Media.Animation.DoubleAnimation(1, TimeSpan.FromMilliseconds(220)) { EasingFunction = ease });
            transform.BeginAnimation(ScaleTransform.ScaleYProperty, new System.Windows.Media.Animation.DoubleAnimation(1, TimeSpan.FromMilliseconds(220)) { EasingFunction = ease });
            PrimaryButton.Focus();
        };
        PreviewKeyDown += OnKeyDown;
    }

    private void OnKeyDown(object sender, KeyEventArgs e)
    {
        if (e.Key == Key.Escape)
        {
            e.Handled = true;
            Answered?.Invoke(false);
        }
        else if (e.Key == Key.Enter)
        {
            e.Handled = true;
            Answered?.Invoke(true);
        }
    }

    private void Primary_Click(object sender, RoutedEventArgs e) => Answered?.Invoke(true);
    private void Cancel_Click(object sender, RoutedEventArgs e) => Answered?.Invoke(false);
}
