package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootApplication
@RestController
public class SlackNotifier {

    // 直接URLを書くのではなく、OSの設定(環境変数)から読み込むように変更
    private static final String SLACK_WEBHOOK_URL = System.getenv("SLACK_WEBHOOK_URL");

    public static void main(String[] args) {
        SpringApplication.run(SlackNotifier.class, args);
    }

    @GetMapping("/push")
    public String push(@RequestParam(name = "url") String targetUrl) {
        try {
            Document doc = Jsoup.connect(targetUrl)
                    .userAgent(
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Referer", "https://www.athome.co.jp/")
                    .timeout(20000)
                    .get();

            // 1. ピンポイントで「該当物件数」の数字を狙い撃ち
            // あなたが見つけてくれたクラス名を最優先で探します
            String totalCount = doc.select(".area-top__property--number").text();

            // 数字以外（「件」など）が混ざっていたら消して数字だけにする
            totalCount = totalCount.replaceAll("[^0-9]", "");

            // 2. 物件詳細リンク（10桁の数字）を抽出
            Elements links = doc.select("a[href*='/kodate/']");
            java.util.LinkedHashSet<String> cleanUrls = new java.util.LinkedHashSet<>();
            java.util.Map<String, String> urlToName = new java.util.HashMap<>();

            for (Element link : links) {
                String href = link.attr("abs:href").split("\\?")[0];
                if (href.matches(".*/kodate/[0-9]{10}/")) {
                    cleanUrls.add(href);
                    String text = link.text().trim();
                    // 長いテキスト（情報が詰まっているもの）を優先的に名前に採用
                    if (text.length() > 15 && !urlToName.containsKey(href)) {
                        urlToName.put(href, text);
                    }
                }
            }

            if (cleanUrls.isEmpty())
                return "物件が見つかりませんでした。";

            StringBuilder sb = new StringBuilder();
            sb.append("📢 【全 " + totalCount + " 件ヒット / 最新50件】\n");
            sb.append("----------------------------\n");

            int count = 0;
            for (String url : cleanUrls) {
                count++;
                String name = urlToName.getOrDefault(url, "物件詳細（クリックで確認）");
                // 情報を1行に整形
                if (name.length() > 60)
                    name = name.substring(0, 57) + "...";

                sb.append(count + ". " + name + "\n");
                sb.append("   🔗 " + url + "\n\n");
                if (count >= 50)
                    break;
            }

            sb.append("----------------------------\n");
            // 3. 調べた条件のリンクも通知に含める
            sb.append("🔍 検索条件（アットホーム）:\n" + targetUrl);

            sendToSlack(sb.toString());
            return "Slackに送信完了！（全 " + totalCount + " 件）";

        } catch (Exception e) {
            return "エラー: " + e.getMessage();
        }
    }

    private void sendToSlack(String message) throws Exception {
        String json = "{\"text\":\"" + message + "\"}";
        HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create(SLACK_WEBHOOK_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}