package com.youyoudezhuzhu.quizsearch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

/** 构建并更新通知栏里的「搜题」通知。 */
public final class NotificationHelper {
    public static final String CHANNEL_ID = "quiz_search_shade";
    public static final int NOTIFICATION_ID = 1001;
    public static final String KEY_QUERY = "query_text";
    private static final int MAX_MATCHES = 20;

    private NotificationHelper() {
    }

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "通知栏搜题", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("在通知栏里直接搜索安规题库");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        channel.setSound(null, null);
        nm.createNotificationChannel(channel);
    }

    public static boolean canPost(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && nm.areNotificationsEnabled();
    }

    public static void cancel(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NOTIFICATION_ID);
    }

    public static void post(Context context) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        ensureChannel(context);
        if (!nm.areNotificationsEnabled()) return;

        Prefs prefs = new Prefs(context);
        String query = prefs.getQuery();
        List<SearchEngine.Match> matches = SearchEngine.search(
                QuestionStore.get(context), query, MAX_MATCHES);
        List<ContentFormatter.Page> pages = ContentFormatter.buildPages(matches);
        int pageIndex = pages.isEmpty() ? 0
                : Math.min(Math.max(0, prefs.getIndex()), pages.size() - 1);
        boolean hasQuery = query != null && !query.trim().isEmpty();

        String title;
        String summary;
        String bigText;
        String subText;
        if (!hasQuery) {
            title = "安规搜题 · 通知栏搜题";
            summary = "点「搜索」输入题目关键词，答案直接显示在这里";
            subText = "通知栏直接搜题";
            bigText = "在通知栏里搜索 119 道安规 / 变电检修题，全程不用打开 App。\n\n"
                    + "① 下拉通知栏，找到这条「安规搜题」通知\n"
                    + "② 点「搜索」，输入题干关键词（如：安全带 高挂低用）后发送\n"
                    + "③ 答案和选项会直接显示在这条通知里\n"
                    + "④ 内容较长时点「下一条」翻页；点「关闭」移除通知栏\n\n"
                    + "点本条通知可回到设置页。";
        } else if (matches.isEmpty()) {
            title = "未找到匹配题目";
            summary = "关键词：" + query + "，试试题干里更短的词";
            subText = "无匹配";
            bigText = "没有找到与「" + query + "」匹配的题目。\n\n"
                    + "可尝试更短的关键词，例如：\n"
                    + "· 安全带\n· 接地线\n· 工作票\n· 十不干\n· 高处作业\n· 绝缘手套\n\n"
                    + "点「搜索」重新输入。";
        } else {
            ContentFormatter.Page page = pages.get(pageIndex);
            Question q = page.question;
            title = page.title;
            summary = "【答案】" + q.answerDisplay();
            bigText = page.bigText;
            subText = page.subText;
        }

        Intent open = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, 200, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_search)
                .setColor(0xFF2563EB)
                .setContentTitle(title)
                .setContentText(summary)
                .setSubText(subText)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC);

        // 通知栏内联搜索框
        RemoteInput remoteInput = new RemoteInput.Builder(KEY_QUERY)
                .setLabel("输入题目关键词，如：安全带 高挂低用")
                .setAllowFreeFormInput(true)
                .build();
        Intent searchIntent = new Intent(context, SearchReceiver.class)
                .setAction(SearchReceiver.ACTION_SEARCH);
        PendingIntent searchPending = PendingIntent.getBroadcast(
                context, 101, searchIntent, mutableFlags());
        Notification.Action.Builder searchBuilder =
                new Notification.Action.Builder(
                        Icon.createWithResource(context, R.drawable.ic_search), "搜索", searchPending)
                        .addRemoteInput(remoteInput)
                        .setAllowGeneratedReplies(false);
        if (Build.VERSION.SDK_INT >= 28) {
            searchBuilder.setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY);
        }
        builder.addAction(searchBuilder.build());

        if (pages.size() > 1) {
            Intent nextIntent = new Intent(context, SearchReceiver.class)
                    .setAction(SearchReceiver.ACTION_NEXT);
            PendingIntent nextPending = PendingIntent.getBroadcast(
                    context, 102, nextIntent, mutableFlags());
            builder.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_next), "下一条", nextPending).build());
        }
        Intent closeIntent = new Intent(context, SearchReceiver.class)
                .setAction(SearchReceiver.ACTION_CLOSE);
        PendingIntent closePending = PendingIntent.getBroadcast(
                context, 104, closeIntent, mutableFlags());
        builder.addAction(new Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_close), "关闭", closePending).build());

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    /** 「下一条」：翻到下一页（跨题或同题下一页）。 */
    public static void moveNext(Context context) {
        Prefs prefs = new Prefs(context);
        List<SearchEngine.Match> matches = SearchEngine.search(
                QuestionStore.get(context), prefs.getQuery(), MAX_MATCHES);
        int total = ContentFormatter.buildPages(matches).size();
        if (total > 0) {
            prefs.setIndex((Math.max(0, prefs.getIndex()) + 1) % total);
        }
        post(context);
    }

    private static int mutableFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
        return flags;
    }

    static String formatQuestion(Question q) {
        StringBuilder sb = new StringBuilder();
        sb.append("【题目】").append(q.stem).append('\n');
        if (!q.essay) {
            for (int i = 0; i < q.optionKeys.length; i++) {
                sb.append('\n').append(q.optionKeys[i]).append(". ").append(q.optionTexts[i]);
            }
        }
        sb.append("\n【答案】");
        if (q.essay) {
            if (q.answerNote != null && !q.answerNote.isEmpty()) {
                sb.append("（").append(q.answerNote).append("）\n");
            }
            sb.append(q.answerText);
        } else {
            sb.append(q.answerDisplay());
        }
        return sb.toString();
    }
}
