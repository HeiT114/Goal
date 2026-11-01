package com.goal.app;

import android.app.Application;
import android.content.Context;

public class GoalApp extends Application {
    private static GoalApp instance;
    private Store store;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        store = new Store(this);
        Theme.load(store.data().config);
        NotificationCenter.ensureChannel(this);
        NotificationCenter.schedule(this);
    }

    public static GoalApp get(Context context) {
        return (GoalApp) context.getApplicationContext();
    }

    public static GoalApp instance() {
        return instance;
    }

    public Store store() {
        return store;
    }
}
