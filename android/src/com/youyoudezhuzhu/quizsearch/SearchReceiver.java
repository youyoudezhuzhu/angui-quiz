package com.youyoudezhuzhu.quizsearch;

import android.app.RemoteInput;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** 处理通知栏里的搜索、下一条、关闭操作。 */
public class SearchReceiver extends BroadcastReceiver {
    public static final String ACTION_SEARCH = "com.youyoudezhuzhu.quizsearch.action.SEARCH";
    public static final String ACTION_NEXT = "com.youyoudezhuzhu.quizsearch.action.NEXT";
    public static final String ACTION_CLOSE = "com.youyoudezhuzhu.quizsearch.action.CLOSE";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Prefs prefs = new Prefs(context);

        if (ACTION_SEARCH.equals(action)) {
            Bundle results = RemoteInput.getResultsFromIntent(intent);
            CharSequence input = results == null ? null : results.getCharSequence(NotificationHelper.KEY_QUERY);
            if (input != null) {
                String query = input.toString().trim();
                if (!query.isEmpty()) {
                    prefs.setQuery(query);
                    prefs.setIndex(0);
                    prefs.setEnabled(true);
                }
            }
            NotificationHelper.post(context);
        } else if (ACTION_NEXT.equals(action)) {
            NotificationHelper.moveNext(context);
        } else if (ACTION_CLOSE.equals(action)) {
            prefs.setEnabled(false);
            NotificationHelper.cancel(context);
        }
    }
}
