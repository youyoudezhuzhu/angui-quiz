package com.youyoudezhuzhu.quizsearch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地题库搜索：归一化 + 子串/分词覆盖 + 字符二元组相似度。
 * 纯 Java，不依赖 Android，方便单元测试。
 */
public final class SearchEngine {
    public static final class Match {
        public final Question question;
        public final double score;

        Match(Question question, double score) {
            this.question = question;
            this.score = score;
        }
    }

    private static final double MIN_SCORE = 150.0;

    private SearchEngine() {
    }

    public static List<Match> search(List<Question> questions, String query, int limit) {
        String nq = normalize(query);
        if (nq.isEmpty()) return Collections.emptyList();

        List<String> tokens = new ArrayList<String>();
        String[] rawTokens = query.trim().split("\\s+");
        for (String t : rawTokens) {
            String n = normalize(t);
            if (!n.isEmpty()) tokens.add(n);
        }

        List<Match> matches = new ArrayList<Match>();
        for (Question q : questions) {
            String ns = normalize(q.stem);
            StringBuilder hayBuilder = new StringBuilder(ns);
            for (String opt : q.optionTexts) hayBuilder.append('\n').append(normalize(opt));
            // 问答参考答案也参与关键词匹配
            if (q.answerText != null && !q.answerText.isEmpty()) {
                hayBuilder.append('\n').append(normalize(q.answerText));
            }
            String hay = hayBuilder.toString();

            double score = 0.0;
            if (ns.equals(nq)) {
                score = 1000.0;
            } else if (ns.contains(nq)) {
                score = 700.0 + 300.0 * nq.length() / Math.max(1, ns.length());
            } else if (nq.contains(ns) && ns.length() >= 6) {
                score = 650.0;
            }

            if (!tokens.isEmpty()) {
                int hit = 0;
                for (String t : tokens) {
                    if (hay.contains(t)) hit++;
                }
                double coverage = (double) hit / tokens.size();
                score = Math.max(score, coverage * 520.0);
                double d = dice(nq, ns);
                score = Math.max(score, d * 420.0 * (0.5 + 0.5 * coverage));
                if (hit == tokens.size() && tokens.size() > 1) {
                    score = Math.max(score, 560.0);
                }
            } else {
                double best = 0.0;
                for (String opt : q.optionTexts) {
                    best = Math.max(best, dice(nq, normalize(opt)) * 0.85);
                }
                score = Math.max(score, Math.max(dice(nq, ns), best) * 420.0);
            }

            if (score >= MIN_SCORE) matches.add(new Match(q, score));
        }

        // 插入排序：分数降序，同分按题库顺序（避免使用 Comparator 内部类）
        for (int i = 1; i < matches.size(); i++) {
            Match cur = matches.get(i);
            int j = i - 1;
            while (j >= 0 && less(matches.get(j), cur)) {
                matches.set(j + 1, matches.get(j));
                j--;
            }
            matches.set(j + 1, cur);
        }
        if (matches.size() > limit) {
            return new ArrayList<Match>(matches.subList(0, limit));
        }
        return matches;
    }

    private static boolean less(Match a, Match b) {
        if (a.score != b.score) return a.score < b.score;
        return a.question.seq > b.question.seq;
    }

    /** 去掉空白和标点、转小写；中英文都适用。 */
    public static String normalize(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) continue;
            if (isPunct(c)) continue;
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    private static boolean isPunct(char c) {
        switch (Character.getType(c)) {
            case Character.CONNECTOR_PUNCTUATION:
            case Character.DASH_PUNCTUATION:
            case Character.START_PUNCTUATION:
            case Character.END_PUNCTUATION:
            case Character.INITIAL_QUOTE_PUNCTUATION:
            case Character.FINAL_QUOTE_PUNCTUATION:
            case Character.OTHER_PUNCTUATION:
            case Character.MATH_SYMBOL:
            case Character.CURRENCY_SYMBOL:
            case Character.MODIFIER_SYMBOL:
            case Character.OTHER_SYMBOL:
                return true;
            default:
                return false;
        }
    }

    /** 字符二元组 Dice 相似度，0..1。 */
    public static double dice(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        if (a.length() < 2 || b.length() < 2) {
            return a.contains(b) || b.contains(a) ? 1.0 : 0.0;
        }
        Map<String, Integer> grams = new HashMap<String, Integer>();
        for (int i = 0; i < a.length() - 1; i++) {
            String g = a.substring(i, i + 2);
            Integer v = grams.get(g);
            grams.put(g, v == null ? 1 : v + 1);
        }
        int inter = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer v = grams.get(g);
            if (v != null && v > 0) {
                inter++;
                grams.put(g, v - 1);
            }
        }
        return 2.0 * inter / ((a.length() - 1) + (b.length() - 1));
    }
}
