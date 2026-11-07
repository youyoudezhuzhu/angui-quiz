package com.youyoudezhuzhu.quizsearch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/** 设置页：开启/刷新通知栏搜题、跳转系统通知设置。 */
public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 10;
    private TextView status;
    private Prefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new Prefs(this);

        status = (TextView) findViewById(R.id.status);
        Button enable = (Button) findViewById(R.id.btn_enable);
        Button close = (Button) findViewById(R.id.btn_close);
        Button settings = (Button) findViewById(R.id.btn_settings);

        ClickHandler handler = new ClickHandler(this);
        enable.setOnClickListener(handler);
        close.setOnClickListener(handler);
        settings.setOnClickListener(handler);

        if (!prefs.isEnabled()) prefs.setEnabled(true);
        if (ensureNotificationPermission()) {
            NotificationHelper.post(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        if (prefs.isEnabled() && NotificationHelper.canPost(this)) {
            NotificationHelper.post(this);
        }
    }

    void onEnableClick() {
        prefs.setEnabled(true);
        if (ensureNotificationPermission()) {
            NotificationHelper.post(this);
            Toast.makeText(this, "已刷新通知栏搜题", Toast.LENGTH_SHORT).show();
        }
        refreshStatus();
    }

    void onCloseClick() {
        prefs.setEnabled(false);
        NotificationHelper.cancel(this);
        refreshStatus();
        Toast.makeText(this, "已关闭通知栏搜题", Toast.LENGTH_SHORT).show();
    }

    void onSettingsClick() {
        openNotificationSettings();
    }

    private void refreshStatus() {
        boolean permission = NotificationHelper.canPost(this);
        boolean enabled = prefs.isEnabled();
        StringBuilder sb = new StringBuilder();
        sb.append("题库：119 题（单选 103 · 读图 14 · 问答 2）\n");
        sb.append("搜索范围：题干 / 选项 / 解析（解析仅参与匹配，不显示）\n");
        sb.append("通知权限：").append(permission ? "已开启" : "未开启，点「系统通知设置」打开").append('\n');
        sb.append("通知栏搜题：").append(enabled ? "已开启" : "已关闭");
        if (!enabled) {
            sb.append("\n点「开启通知栏搜题」后再下拉通知栏");
        }
        status.setText(sb);
    }

    /** 确保 API 33+ 的通知权限，返回当前是否可以发通知。 */
    private boolean ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
            return false;
        }
        return NotificationHelper.canPost(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            refreshStatus();
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                NotificationHelper.post(this);
            }
        }
    }

    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= 26) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
        }
        try {
            startActivity(intent);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    /** 命名内部类（不用匿名类，兼容 d8 的已知问题）。 */
    private static final class ClickHandler implements View.OnClickListener {
        private final MainActivity activity;

        ClickHandler(MainActivity activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.btn_enable) {
                activity.onEnableClick();
            } else if (id == R.id.btn_close) {
                activity.onCloseClick();
            } else if (id == R.id.btn_settings) {
                activity.onSettingsClick();
            }
        }
    }
}
