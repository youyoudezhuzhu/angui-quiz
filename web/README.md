# 网页刷题（在线版）

在线地址：<https://youyoudezhuzhu.github.io/angui-quiz/web/>

- 119 道题：单选 103 · 读图 14 · 问答 2
- 题目/选项乱序、答案点击显示、上一题/下一题、题型筛选、错题本、统计与进度保存
- 纯静态，无第三方依赖；手机浏览器可用
- 离线单文件：[dist/变电检修刷题.html](dist/变电检修刷题.html)

## 本地运行

```bash
python3 -m http.server 8080
# 打开 http://127.0.0.1:8080/
```

## 数据来源

`js/questions.js` 由仓库根目录 `data/questions.json` 生成：

```bash
cd .. && python3 tools/sync-data.py
```
