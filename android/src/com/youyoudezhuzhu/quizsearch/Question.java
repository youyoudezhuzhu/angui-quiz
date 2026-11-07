package com.youyoudezhuzhu.quizsearch;

/** 一道题（来自变电检修/安规题库）。 */
public final class Question {
    public final String id;
    public final int seq;
    public final String category;
    public final String stem;
    public final String[] optionKeys;
    public final String[] optionTexts;
    /** 单选题答案字母（A-F）；问答题为空串。 */
    public final String answer;
    /** 问答题参考答案。 */
    public final String answerText;
    public final String answerNote;
    public final String analysis;
    public final boolean essay;

    public Question(String id, int seq, String category, String stem,
                    String[] optionKeys, String[] optionTexts,
                    String answer, String answerText, String answerNote, String analysis) {
        this.id = id;
        this.seq = seq;
        this.category = category;
        this.stem = stem;
        this.optionKeys = optionKeys;
        this.optionTexts = optionTexts;
        this.answer = answer;
        this.answerText = answerText;
        this.answerNote = answerNote;
        this.analysis = analysis;
        this.essay = "问答题".equals(category);
    }

    /** 正确答案的文本（问答题为参考答案）。 */
    public String answerDisplay() {
        if (essay) return answerText == null ? "" : answerText;
        if (answer == null || answer.isEmpty()) return "";
        for (int i = 0; i < optionKeys.length; i++) {
            if (optionKeys[i].equals(answer)) {
                return answer + ". " + optionTexts[i];
            }
        }
        return answer;
    }
}
