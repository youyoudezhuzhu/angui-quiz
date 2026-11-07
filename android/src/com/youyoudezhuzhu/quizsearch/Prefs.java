package com.youyoudezhuzhu.quizsearch;

import android.content.Context;
import android.content.SharedPreferences;

/** 简单状态：是否开启通知栏、最近一次查询、当前结果序号。 */
public final class Prefs {
    private static final String NAME = "quiz_search_prefs";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_QUERY = "last_query";
    private static final String KEY_INDEX = "result_index";

    private final SharedPreferences sp;

    public Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return sp.getBoolean(KEY_ENABLED, true);
    }

    public void setEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public String getQuery() {
        return sp.getString(KEY_QUERY, "");
    }

    public void setQuery(String query) {
        sp.edit().putString(KEY_QUERY, query == null ? "" : query).apply();
    }

    public int getIndex() {
        return sp.getInt(KEY_INDEX, 0);
    }

    public void setIndex(int index) {
        sp.edit().putInt(KEY_INDEX, Math.max(0, index)).apply();
    }
}
