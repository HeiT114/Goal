package com.goal.app;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/** Colour presets, custom accent and light/dark palettes. */
public final class Theme {
    private Theme() {}

    public static final class Preset {
        public final String id, name, description, accent;
        public final int bg, surface, surface2, line, ink, muted;

        Preset(String id, String name, String description, String accent,
               int bg, int surface, int surface2, int line, int ink, int muted) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.accent = accent;
            this.bg = bg;
            this.surface = surface;
            this.surface2 = surface2;
            this.line = line;
            this.ink = ink;
            this.muted = muted;
        }
    }

    public static final class Palette {
        public int bg, surface, surface2, line, ink, muted;
        public int accent, accentDeep, accentSoft, accentUltra, onAccent;
        public int danger = Color.parseColor("#C14B3F");
        public boolean dark;
    }

    private static final List<Preset> PRESETS = new ArrayList<>();

    static {
        PRESETS.add(new Preset("sand", "暖砂", "温暖、克制", "#D9653B",
                Color.parseColor("#F7F2EB"), Color.parseColor("#FFFDF9"), Color.parseColor("#F2EAE0"),
                Color.parseColor("#E8DCCC"), Color.parseColor("#2B2520"), Color.parseColor("#8C8175")));
        PRESETS.add(new Preset("moss", "苔原", "安静、生长", "#5E7F5C",
                Color.parseColor("#F3F5EF"), Color.parseColor("#FCFDF9"), Color.parseColor("#E9EEE3"),
                Color.parseColor("#DCE4D5"), Color.parseColor("#262C24"), Color.parseColor("#7C8575")));
        PRESETS.add(new Preset("ocean", "远海", "清醒、专注", "#3E7796",
                Color.parseColor("#F1F5F7"), Color.parseColor("#FBFDFE"), Color.parseColor("#E6EEF2"),
                Color.parseColor("#D7E3EA"), Color.parseColor("#21292E"), Color.parseColor("#74858E")));
        PRESETS.add(new Preset("dusk", "暮紫", "柔和、想象", "#8A6FB0",
                Color.parseColor("#F5F2F7"), Color.parseColor("#FDFBFE"), Color.parseColor("#EDE6F2"),
                Color.parseColor("#E2D7EA"), Color.parseColor("#2A242E"), Color.parseColor("#877C8F")));
        PRESETS.add(new Preset("rose", "山茶", "明快、亲近", "#C05A70",
                Color.parseColor("#F8F1F3"), Color.parseColor("#FFFCFD"), Color.parseColor("#F4E7EB"),
                Color.parseColor("#EBD9E0"), Color.parseColor("#2F2428"), Color.parseColor("#8E7A80")));
        PRESETS.add(new Preset("mono", "素墨", "纯粹、留白", "#3F3A34",
                Color.parseColor("#F4F2EE"), Color.parseColor("#FCFBF8"), Color.parseColor("#ECE9E2"),
                Color.parseColor("#DED9CE"), Color.parseColor("#2B2925"), Color.parseColor("#807A6F")));
    }

    private static String themeId = "sand";
    private static boolean dark = false;
    private static int customAccent = 0;
    private static Palette palette = build();

    public static List<Preset> presets() {
        return PRESETS;
    }

    public static Preset preset(String id) {
        for (Preset p : PRESETS) if (p.id.equals(id)) return p;
        return PRESETS.get(0);
    }

    public static void load(Models.Config config) {
        themeId = config.theme == null ? "sand" : config.theme;
        dark = config.dark;
        customAccent = parse(config.accent);
        palette = build();
    }

    public static Palette get() {
        return palette;
    }

    public static boolean isDark() {
        return dark;
    }

    public static String themeId() {
        return themeId;
    }

    private static Palette build() {
        Preset base = preset(themeId);
        Palette p = new Palette();
        p.dark = dark;
        int baseAccent = customAccent != 0 ? customAccent : parse(base.accent);
        if (dark) {
            p.bg = blend(Color.parseColor("#17130F"), baseAccent, 0.045f);
            p.surface = blend(Color.parseColor("#211C17"), baseAccent, 0.08f);
            p.surface2 = blend(Color.parseColor("#2C261F"), baseAccent, 0.11f);
            p.line = blend(Color.parseColor("#40382F"), baseAccent, 0.18f);
            p.ink = Color.parseColor("#F6EFE5");
            p.muted = Color.parseColor("#B0A394");
            p.danger = Color.parseColor("#E08A80");
        } else {
            p.bg = base.bg;
            p.surface = base.surface;
            p.surface2 = base.surface2;
            p.line = base.line;
            p.ink = base.ink;
            p.muted = base.muted;
        }
        p.accent = dark ? lighten(baseAccent, 0.14f) : baseAccent;
        p.accentDeep = dark ? lighten(baseAccent, 0.28f) : darken(baseAccent, 0.18f);
        p.accentSoft = blend(p.accent, p.surface, dark ? 0.34f : 0.30f);
        p.accentUltra = blend(p.accent, p.surface, dark ? 0.12f : 0.08f);
        p.onAccent = luminance(p.accent) > 0.62f
                ? Color.parseColor("#2A2119") : Color.parseColor("#FFF9F2");
        return p;
    }

    public static int parse(String hex) {
        if (hex == null) return 0;
        String value = hex.trim();
        if (!value.startsWith("#")) value = "#" + value;
        if (value.length() != 7 && value.length() != 9) return 0;
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    public static String normalizeHex(String value) {
        int color = parse(value);
        if (color == 0) return null;
        return String.format("#%06X", 0xFFFFFF & color);
    }

    public static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    public static int alpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int blend(int a, int b, float t) {
        float safe = Math.max(0f, Math.min(1f, t));
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bgc = Color.green(b), bb = Color.blue(b);
        return Color.rgb(
                Math.round(ar + (br - ar) * safe),
                Math.round(ag + (bgc - ag) * safe),
                Math.round(ab + (bb - ab) * safe));
    }

    public static int tint(int target, int accent, float amount) {
        return blend(target, accent, amount);
    }

    public static float luminance(int color) {
        return (0.2126f * Color.red(color) + 0.7152f * Color.green(color)
                + 0.0722f * Color.blue(color)) / 255f;
    }

    public static int lighten(int color, float amount) {
        return blend(color, Color.WHITE, amount);
    }

    public static int darken(int color, float amount) {
        return blend(color, Color.BLACK, amount);
    }

    public static int withAlpha(int color, float alpha) {
        return (color & 0x00FFFFFF) | (Math.round(Math.max(0f, Math.min(1f, alpha)) * 255) << 24);
    }
}
