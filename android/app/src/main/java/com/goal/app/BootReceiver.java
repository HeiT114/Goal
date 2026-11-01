package com.goal.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores the daily reminder after a reboot. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationCenter.schedule(context);
    }
}
