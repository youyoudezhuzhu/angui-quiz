package com.youyoudezhuzhu.quizsearch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机 / 升级后恢复通知栏搜题。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (new Prefs(context).isEnabled() && NotificationHelper.canPost(context)) {
                NotificationHelper.post(context);
            }
        }
    }
}
