using System.Windows;
using System.Windows.Media;
using Goal.Models;

namespace Goal.Services;

public sealed record ThemePreset(string Id, string Name, string Accent, string Description);

public static class ThemeService
{
    public static readonly ThemePreset[] Presets =
    {
        new("sand", "暖砂", "#D9653B", "温暖、克制"),
        new("moss", "苔原", "#5E7F5C", "安静、生长"),
        new("ocean", "远海", "#3E7796", "清醒、专注"),
        new("dusk", "暮紫", "#8A6FB0", "柔和、想象"),
        new("rose", "山茶", "#C05A70", "明快、亲近"),
        new("mono", "素墨", "#3F3A34", "纯粹、留白")
    };

    public static ThemePreset? Find(string id) => Presets.FirstOrDefault(p => p.Id == id);

    public static void Apply(AppConfig config)
    {
        var accent = Parse(config.Accent) ?? Parse("#D9653B")!.Value;
        var dark = config.Dark;

        Color bg = dark ? Hex("#141210") : Hex("#F6F2EA");
        Color sidebar = dark ? Hex("#191714") : Hex("#FBF8F2");
        Color surface = dark ? Hex("#201D19") : Hex("#FFFDF9");
        Color surface2 = dark ? Hex("#2A2621") : Hex("#F1EBE2");
        Color ink = dark ? Hex("#F3EDE3") : Hex("#211E19");
        Color muted = dark ? Hex("#A69C8E") : Hex("#7C7468");
        Color line = dark ? Hex("#37312A") : Hex("#E8E0D5");
        Color success = dark ? Hex("#7FB897") : Hex("#4C8C6A");
        Color danger = dark ? Hex("#E58A84") : Hex("#BF5353");
        Color accentDeep = dark ? Lighten(accent, 0.22) : Darken(accent, 0.22);
        Color accentSoft = Blend(accent, surface, dark ? 0.22 : 0.14);
        Color accentUltra = Blend(accent, surface, dark ? 0.10 : 0.07);
        Color tintTop = Blend(accent, bg, dark ? 0.055 : 0.045);

        SetBrush("BgBrush", bg);
        SetBrush("SidebarBrush", sidebar);
        SetBrush("SurfaceBrush", surface);
        SetBrush("Surface2Brush", surface2);
        SetBrush("InkBrush", ink);
        SetBrush("MutedBrush", muted);
        SetBrush("LineBrush", line);
        SetBrush("AccentBrush", accent);
        SetBrush("AccentDeepBrush", accentDeep);
        SetBrush("AccentSoftBrush", accentSoft);
        SetBrush("AccentUltraSoftBrush", accentUltra);
        SetBrush("SuccessBrush", success);
        SetBrush("DangerBrush", danger);
        SetBrush("WhiteBrush", Colors.White);
        SetBrush("OnAccentBrush", IsBright(accent) ? Hex("#241A12") : Colors.White);
        SetBrush("ScrimBrush", Color.FromArgb(150, 10, 8, 6));

        var bgGradient = new LinearGradientBrush(bg, tintTop, new Point(0, 0), new Point(1, 1));
        Application.Current.Resources["BgGradientBrush"] = bgGradient;
        var glow = new RadialGradientBrush
        {
            GradientOrigin = new Point(0.7, 0.15),
            Center = new Point(0.7, 0.15),
            RadiusX = 0.8,
            RadiusY = 0.8
        };
        glow.GradientStops.Add(new GradientStop(Color.FromArgb((byte)(dark ? 30 : 40), accent.R, accent.G, accent.B), 0));
        glow.GradientStops.Add(new GradientStop(Color.FromArgb(0, accent.R, accent.G, accent.B), 1));
        Application.Current.Resources["AmbientGlowBrush"] = glow;
        var accentGradient = new LinearGradientBrush(accent, accentDeep, new Point(0, 0), new Point(1, 1));
        Application.Current.Resources["AccentGradientBrush"] = accentGradient;
    }

    public static Brush BrushFromHex(string hex)
    {
        var color = Parse(hex) ?? Hex("#D9653B");
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    public static Color ParseColor(string? hex) => Parse(hex) ?? Hex("#D9653B");

    public static Color Blend(Color a, Color b, double amountA)
    {
        amountA = Math.Clamp(amountA, 0, 1);
        return Color.FromRgb(
            (byte)Math.Round(a.R * amountA + b.R * (1 - amountA)),
            (byte)Math.Round(a.G * amountA + b.G * (1 - amountA)),
            (byte)Math.Round(a.B * amountA + b.B * (1 - amountA)));
    }

    private static void SetBrush(string key, Color color)
    {
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        Application.Current.Resources[key] = brush;
    }

    private static Color? Parse(string? hex)
    {
        var normalized = StorageService.NormalizeHex(hex);
        return normalized is null ? null : (Color)ColorConverter.ConvertFromString(normalized);
    }

    private static Color Hex(string value) => (Color)ColorConverter.ConvertFromString(value);

    private static Color Darken(Color c, double amount)
        => Color.FromRgb((byte)(c.R * (1 - amount)), (byte)(c.G * (1 - amount)), (byte)(c.B * (1 - amount)));

    private static Color Lighten(Color c, double amount)
        => Color.FromRgb((byte)(c.R + (255 - c.R) * amount), (byte)(c.G + (255 - c.G) * amount), (byte)(c.B + (255 - c.B) * amount));

    private static bool IsBright(Color c) => (c.R * 299 + c.G * 587 + c.B * 114) / 1000 > 190;
}
