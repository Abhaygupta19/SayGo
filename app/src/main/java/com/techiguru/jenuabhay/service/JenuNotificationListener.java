package com.techiguru.jenuabhay.service;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class JenuNotificationListener extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pack = sbn.getPackageName();
        String title = sbn.getNotification().extras.getString("android.title");
        String text = sbn.getNotification().extras.getString("android.text");
        
        Log.d("JenuNotification", "From: " + pack + " Title: " + title + " Text: " + text);
        // Here you could trigger a voice announcement if the assistant is active
    }
}