import com.youyoudezhuzhu.quizsearch.ContentFormatter;
import com.youyoudezhuzhu.quizsearch.Question;
import com.youyoudezhuzhu.quizsearch.SearchEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 纯 JVM 测试：不依赖 Android。 */
public class TestSearch {
    private static int checks = 0;
    private static int failures = 0;

    public static void main(String[] args) throws Exception {
        List<Question> questions = load(args[0]);
        check("题库条数 = 119", questions.size() == 119, "size=" + questions.size());

        // 1) 题干中间 8 字片段应当召回本题（前 5 条内）
        int total = 0, ok = 0;
        int printed = 0;
        for (Question q : questions) {
            String stem = q.stem;
            if (stem.length() < 12 || stem.contains("图上有")) continue;
            int mid = stem.length() / 2;
            int start = Math.max(0, Math.min(stem.length() - 8, mid - 4));
            String query = stem.substring(start, start + 8);
            List<SearchEngine.Match> ms = SearchEngine.search(questions, query, 5);
            total++;
            boolean found = false;
            for (SearchEngine.Match m : ms) {
                if (m.question.id.equals(q.id)) found = true;
            }
            if (found) ok++;
            else if (printed++ < 5) {
                System.out.println("  [miss] " + q.id + " query=" + query + " stem=" + stem);
            }
        }
        check("题干片段召回 >= 95% (" + ok + "/" + total + ")",
                total > 0 && ok * 100 >= total * 95, ok + "/" + total);

        // 2) 关键词都能搜到，且首条包含关键词
        String[] keywords = {"安全带", "工作票", "接地线", "十不干", "高处作业",
                "绝缘手套", "心肺复苏", "工作负责人", "违章", "安全距离"};
        for (String kw : keywords) {
            List<SearchEngine.Match> ms = SearchEngine.search(questions, kw, 20);
            check("关键词「" + kw + "」有结果", !ms.isEmpty(), "count=" + ms.size());
            if (!ms.isEmpty()) {
                check("关键词「" + kw + "」首条相关", related(ms.get(0).question, kw),
                        ms.get(0).question.stem);
            }
        }

        // 3) 多关键词（空格分隔）
        List<SearchEngine.Match> spaced = SearchEngine.search(questions, "安全带 高挂低用", 10);
        check("多关键词「安全带 高挂低用」有结果", !spaced.isEmpty(), "");
        if (!spaced.isEmpty()) {
            check("多关键词首条包含「安全带」", related(spaced.get(0).question, "安全带"),
                    spaced.get(0).question.stem);
        }

        // 4) 无结果
        check("无意义查询返回空", SearchEngine.search(questions, "zzzzqqqq", 10).isEmpty(),
                "");

        // 5) 归一化
        check("标点/空格归一化", SearchEngine.normalize(" 安全带，高挂低用。 ").equals("安全带高挂低用"),
                SearchEngine.normalize(" 安全带，高挂低用。 "));

        // 6) 完整题干命中第一条
        Question exact = null;
        for (Question q : questions) {
            if (q.stem.length() > 16 && !q.stem.contains("图上有")) {
                exact = q;
                break;
            }
        }
        if (exact != null) {
            List<SearchEngine.Match> ms = SearchEngine.search(questions, exact.stem, 5);
            check("完整题干命中第一条", !ms.isEmpty() && ms.get(0).question.id.equals(exact.id),
                    ms.isEmpty() ? "empty" : ms.get(0).question.id);
        }

        // 7) answerDisplay 正确性
        Question single = null, essay = null;
        for (Question q : questions) {
            if (single == null && !q.essay && !q.answer.isEmpty()) single = q;
            if (essay == null && q.essay) essay = q;
        }
        if (single != null) {
            String disp = single.answerDisplay();
            check("单选答案展示以字母开头", disp.startsWith(single.answer + ". "), disp);
        }
        if (essay != null) {
            check("问答答案展示非空", essay.answerDisplay().length() > 10,
                    essay.answerDisplay().substring(0, Math.min(20, essay.answerDisplay().length())));
        }

        // 8) 通知正文只含「题目 / 选项 / 答案」，不含解析
        int noAnswer = 0, hasAnalysis = 0;
        for (Question q : questions) {
            String content = ContentFormatter.formatQuestion(q);
            if (!content.contains("【答案】")) noAnswer++;
            if (content.contains("【解析】")) hasAnalysis++;
            String expect = q.essay ? q.answerText : q.answerDisplay();
            if (!expect.isEmpty() && !content.contains(expect)) noAnswer++;
        }
        check("所有题正文含【答案】与答案内容", noAnswer == 0, "bad=" + noAnswer);
        check("所有题正文不含【解析】", hasAnalysis == 0, "bad=" + hasAnalysis);

        // 9) 长答案自动分页、每页不超过限制
        List<SearchEngine.Match> essayMatches = SearchEngine.search(questions, "十不干", 20);
        List<ContentFormatter.Page> pages = ContentFormatter.buildPages(essayMatches);
        int essayPages = 0;
        for (ContentFormatter.Page p : pages) {
            if (p.question.essay) essayPages++;
        }
        check("问答长答案自动分页（十不干 >= 2 页）", essayPages >= 2, "pages=" + essayPages);
        int over = 0;
        for (ContentFormatter.Page p : pages) {
            if (ContentFormatter.estimateLines(p.bigText) > ContentFormatter.MAX_LINES) over++;
        }
        check("分页后每页不超过行数上限", over == 0, "over=" + over);

        // 10) 长题干题（x078）的答案仍能在分页内容中找到
        Question longest = null;
        for (Question q : questions) if ("x078".equals(q.id)) longest = q;
        if (longest != null) {
            List<SearchEngine.Match> lm = SearchEngine.search(questions, longest.stem, 5);
            List<ContentFormatter.Page> lp = ContentFormatter.buildPages(lm);
            boolean found = false;
            for (ContentFormatter.Page p : lp) {
                if (p.question.id.equals(longest.id) && p.bigText.contains(longest.answerDisplay())) found = true;
            }
            check("最长题干题答案仍完整可见", found, "pages=" + lp.size());
        }

        // 11) 一页装下的题目比例（长题才会分页）
        int singlePage = 0, counted = 0;
        for (Question q : questions) {
            List<SearchEngine.Match> one = SearchEngine.search(questions, q.stem, 1);
            if (one.isEmpty() || !one.get(0).question.id.equals(q.id)) continue;
            counted++;
            if (ContentFormatter.buildPages(one).size() == 1) singlePage++;
        }
        check("至少 95% 的题可一页显示完整内容（" + singlePage + "/" + counted + "）",
                counted > 0 && singlePage * 100 >= counted * 95, singlePage + "/" + counted);

        // 12) 性能：50 次搜索总耗时
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 50; i++) SearchEngine.search(questions, "工作票", 20);
        long cost = System.currentTimeMillis() - t0;
        check("50 次搜索 < 2000ms", cost < 2000, cost + "ms");
        System.out.println("  50 次搜索耗时: " + cost + "ms");

        System.out.println("\n==== SUMMARY ====");
        System.out.println("PASS " + (checks - failures) + "/" + checks);
        if (failures > 0) System.exit(1);
    }

    private static boolean related(Question q, String kw) {
        String hay = SearchEngine.normalize(q.stem);
        for (String opt : q.optionTexts) hay += SearchEngine.normalize(opt);
        if (q.analysis != null) hay += SearchEngine.normalize(q.analysis);
        return hay.contains(SearchEngine.normalize(kw));
    }

    private static void check(String name, boolean ok, String detail) {
        checks++;
        if (!ok) failures++;
        System.out.println((ok ? "PASS" : "FAIL") + " - " + name + (detail != null && !detail.isEmpty() ? " :: " + detail : ""));
    }

    private static String b64(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    private static List<Question> load(String path) throws Exception {
        List<Question> list = new ArrayList<Question>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) continue;
            String[] f = line.split("\t", -1);
            String[] keys = b64(f[4]).isEmpty() ? new String[0] : b64(f[4]).split("\u0001", -1);
            String[] texts = b64(f[5]).isEmpty() ? new String[0] : b64(f[5]).split("\u0001", -1);
            list.add(new Question(b64(f[0]), Integer.parseInt(b64(f[1])), b64(f[2]), b64(f[3]),
                    keys, texts, b64(f[6]), b64(f[7]), b64(f[8]), b64(f[9])));
        }
        return list;
    }
}
