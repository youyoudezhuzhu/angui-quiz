package com.youyoudezhuzhu.quizsearch;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** 从 assets/questions.json 懒加载题库。 */
public final class QuestionStore {
    private static List<Question> cache;

    private QuestionStore() {
    }

    public static synchronized List<Question> get(Context context) {
        if (cache != null) return cache;
        List<Question> list = new ArrayList<Question>();
        InputStream in = null;
        try {
            in = context.getAssets().open("questions.json");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            JSONArray arr = new JSONArray(new String(bos.toByteArray(), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                JSONArray opts = o.optJSONArray("options");
                int oc = opts == null ? 0 : opts.length();
                String[] keys = new String[oc];
                String[] texts = new String[oc];
                for (int j = 0; j < oc; j++) {
                    JSONObject op = opts.getJSONObject(j);
                    keys[j] = op.optString("key");
                    texts[j] = op.optString("text");
                }
                list.add(new Question(
                        o.optString("id"),
                        o.optInt("seq", i + 1),
                        o.optString("category"),
                        o.optString("stem"),
                        keys, texts,
                        o.optString("answer"),
                        o.optString("answerText"),
                        o.optString("answerNote"),
                        o.optString("analysis")));
            }
            cache = list;
        } catch (Exception e) {
            cache = list;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
        return cache;
    }
}
