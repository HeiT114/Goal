package com.goal.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Local, opt-out reminders for plans that are due or overdue. */
public final class NotificationCenter {
    private NotificationCenter() {}

    public static final String CHANNEL_ID = "goal-reminders";
    private static final String PREFS = "goal-notifications";
    private static final String KEY_ENABLED = "enabled";
    private static final int NOTIFICATION_ID = 4101;
    private static final int ALARM_REQUEST = 4102;

    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply();
        if (enabled) schedule(context);
        else cancel(context);
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "计划提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("每天提醒今天到期或已逾期的计划。");
        channel.setShowBadge(true);
        manager.createNotificationChannel(channel);
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < 33) return true;
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT < 33 || hasPermission(activity)) return;
        activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 2201);
    }

    public static void schedule(Context context) {
        if (!isEnabled(context)) return;
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context, ALARM_REQUEST, intent, flags);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 9);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pending);
    }

    public static void cancel(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context, ALARM_REQUEST, intent, flags);
        manager.cancel(pending);
    }

    public static void showDueReminder(Context context) {
        if (!isEnabled(context) || !hasPermission(context)) return;
        ensureChannel(context);
        Store store = GoalApp.get(context).store();
        LocalDate today = LocalDate.now();
        List<Models.Task> due = new ArrayList<>();
        for (Models.Task task : store.tasks()) {
            if (task.done || task.dueDate == null || task.dueDate.isEmpty()) continue;
            try {
                LocalDate date = LocalDate.parse(task.dueDate);
                if (!date.isAfter(today)) due.add(task);
            } catch (Exception ignored) {
            }
        }
        if (due.isEmpty()) return;

        int overdue = 0;
        for (Models.Task task : due) {
            try {
                if (LocalDate.parse(task.dueDate).isBefore(today)) overdue++;
            } catch (Exception ignored) {
            }
        }
        String title = overdue > 0
                ? "计划提醒 · " + due.size() + " 项待处理"
                : "今天有 " + due.size() + " 项计划到期";
        StringBuilder body = new StringBuilder();
        int shown = Math.min(4, due.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) body.append("\n");
            body.append("· ").append(due.get(i).title);
        }
        if (due.size() > shown) body.append("\n· 还有 ").append(due.size() - shown).append(" 项");

        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent content = PendingIntent.getActivity(context, 0, open, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body.toString().replace("\n", " · "))
                .setStyle(new Notification.BigTextStyle().bigText(body.toString()))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setShowWhen(true);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, builder.build());
    }
}
