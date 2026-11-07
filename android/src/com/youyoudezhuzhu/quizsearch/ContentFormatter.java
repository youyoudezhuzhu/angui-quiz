package com.youyoudezhuzhu.quizsearch;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知栏正文排版：只显示「题目 / 选项 / 答案」（不含解析）。
 * 通知栏正文高度由系统限制（约 12–13 行、不能滚动），
 * 这里按“估算显示行数”分页，尽量把一道题排在一页里。
 * 纯 Java，方便单元测试。
 */
public final class ContentFormatter {
    /** 单页最多行数：系统展开通知正文大约能显示 12–13 行，取 12 保证不被截断。 */
    public static final int MAX_LINES = 12;
    /** 每行宽度单位：中文/全角字符按 2，ASCII 按 1；约 18 个汉字一行。 */
    public static final int UNITS_PER_LINE = 36;

    public static final class Page {
        public final Question question;
        public final String title;
        public final String bigText;
        public final String subText;

        Page(Question question, String title, String bigText, String subText) {
            this.question = question;
            this.title = title;
            this.bigText = bigText;
            this.subText = subText;
        }
    }

    private ContentFormatter() {
    }

    public static List<Page> buildPages(List<SearchEngine.Match> matches) {
        List<Page> pages = new ArrayList<Page>();
        int total = matches.size();
        for (int i = 0; i < total; i++) {
            Question q = matches.get(i).question;
            List<String> chunks = splitByLines(formatQuestion(q), MAX_LINES);
            int pageCount = chunks.size();
            for (int p = 0; p < pageCount; p++) {
                String title = "命中 " + total + " 条 · 第 " + (i + 1) + " 条";
                if (pageCount > 1) {
                    title += " · 第 " + (p + 1) + "/" + pageCount + " 页";
                }
                String sub = q.category;
                if (q.seq > 0) sub += " · 题库第 " + q.seq + " 题";
                pages.add(new Page(q, title, chunks.get(p), sub));
            }
        }
        return pages;
    }

    /** 题目 + 选项 + 答案（不含解析）。 */
    public static String formatQuestion(Question q) {
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
            } else {
                sb.append('\n');
            }
            sb.append(q.answerText);
        } else {
            sb.append(q.answerDisplay());
        }
        return sb.toString();
    }

    /** 估算文本在通知栏里的显示行数。 */
    public static int estimateLines(String text) {
        int lines = 0;
        String[] paragraphs = text.split("\n", -1);
        for (String p : paragraphs) {
            if (p.isEmpty()) continue;
            lines += paragraphLines(p);
        }
        return Math.max(1, lines);
    }

    private static int paragraphLines(String p) {
        int units = widthUnits(p);
        return Math.max(1, (units + UNITS_PER_LINE - 1) / UNITS_PER_LINE);
    }

    /** 中文/全角字符宽度按 2，其余按 1。 */
    public static int widthUnits(String s) {
        int units = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isWide(c)) units += 2;
            else units += 1;
        }
        return units;
    }

    private static boolean isWide(char c) {
        return Character.isIdeographic(c)
                || (c >= 0x3000 && c <= 0x303F)
                || (c >= 0xFE30 && c <= 0xFE4F)
                || (c >= 0xFF00 && c <= 0xFF60);
    }

    /** 按“行”分页：每页不超过 maxLines 行，尽量不拆开选项。 */
    public static List<String> splitByLines(String text, int maxLines) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        int curLines = 0;

        String[] paragraphs = text.split("\n", -1);
        for (String p : paragraphs) {
            if (p.isEmpty()) continue;
            int need = paragraphLines(p);
            if (need > maxLines) {
                // 单段就超过一页：先结束当前页，再按宽度硬切
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                    curLines = 0;
                }
                int maxUnits = UNITS_PER_LINE * maxLines;
                int start = 0;
                while (start < p.length()) {
                    int end = start;
                    int units = 0;
                    while (end < p.length()) {
                        int w = isWide(p.charAt(end)) ? 2 : 1;
                        if (units + w > maxUnits) break;
                        units += w;
                        end++;
                    }
                    if (end == start) end++;
                    out.add(p.substring(start, end));
                    start = end;
                }
                continue;
            }
            if (curLines + need > maxLines) {
                out.add(cur.toString());
                cur.setLength(0);
                curLines = 0;
            }
            if (cur.length() > 0) cur.append('\n');
            cur.append(p);
            curLines += need;
        }
        if (cur.length() > 0) out.add(cur.toString());
        if (out.isEmpty()) out.add("");
        return out;
    }
}
