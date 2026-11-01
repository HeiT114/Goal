package com.goal.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires once a day, shows the due reminder, then schedules tomorrow. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationCenter.showDueReminder(context);
        NotificationCenter.schedule(context);
    }
}
