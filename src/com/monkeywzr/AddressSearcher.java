package com.monkeywzr;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * インデックスデータのフォーマット:
 * トークン,行目1,行目2,行目3,行目4,行目5,...
 *
 * TODO: データソースの初期処理(複数行に分散されたデータを一行にする)
 *
 * インデックスについて:
 *    - インデックスファイル名: [source]_index.csv
 *    - データソースの住所情報[都道府県, 市区町村, 町域]をぞれぞれbi-gramトークン化し、位置情報(行目)を記録
 *    - トークン化の例:
 *        [東京都, 江東区, 大島] --> [東京, 京都, 江東, 東区, 大島]
 *
 * 検索処理の流れ
 *    - 検索文字列をトークン化
 *    - トークンごとでインデックスから位置情報を取得
 *    - 位置情報情報をウェイトを計算する（同一行目のヒット回数）　see: searchTokens
 *    - 該当行目のデータをソースから取得、ウェイトの降順で表示する
 *
 *
 */
public class AddressSearcher {
    // ファイルのcharset
    private final Charset charset;

    // データソース(行ごと)
    private List<String> source;

    /**
     * インデックスMap
     * key: 文字トーク, value: 位置情報(行目)のまとめ
     * 例：
     *   key: "東京", value: "1,2,3,4,5,6,7,88,999"
     */
    private Map<String, String> indexes;

    public AddressSearcher(String sourceFilePath) {
        // TODO: charsetが指定可能にする
        this.charset = Charset.forName("SJIS");
        try {
            this.load(sourceFilePath, charset);
        } catch (IOException e) {
            System.out.println("初期化失敗しました");
            e.printStackTrace();
        }
    }

    public List<String> search(String str) {
        Map<Integer, Integer> lineNos = searchTokens(biGramTokenizer(str));
        // 降順で返す
        return lineNos.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> source.get(entry.getKey()))
                .collect(Collectors.toList());
    }

    // データソースとインデックスファイルをロードする
    // 初期起動の場合、インデックスファイル作成処理を行う
    private void load(String sourceFilePath, Charset charset) throws IOException {
        try (BufferedReader sourceBr = new BufferedReader(new FileReader(sourceFilePath, charset))) {
            // 分割された複数レコードを結合する(全角となっている町域部分の文字数が38文字を越える場合、また半角となっているフリガナ部分の文字数が76文字を越える場合)
            this.source = sourceBr.lines()
                    .map(line -> line.split(","))
                    // 郵便番号で結合する
                    .collect(Collectors.groupingBy(fields -> fields[2]))
                    .values().stream()
                    .flatMap(group -> {
                        String[] base = group.get(0);
                        if (group.size() <= 1 || !base[12].equals("0")) {
                            return group.stream().map(arr -> String.join(",", arr));
                        }
                        group.forEach(arr -> System.out.println(Arrays.toString(arr)));
                        // 町域フリガナ部分の結合
                        base[5] = joinField(group, 5);
                        // 町域部分の結結合
                        base[8] = joinField(group, 8);
                        return Stream.of(String.join(",", base));
                    }).collect(Collectors.toUnmodifiableList());

            // インデックスファイルをロードする
            File index = new File(sourceFilePath.split("\\.")[0] + "_index.csv");
            if (!index.exists()) {
                index = this.createIndex(index.getPath(), this.source);
            }
            try (BufferedReader indexBr = new BufferedReader(new FileReader(index, charset))) {
                this.indexes = indexBr.lines().map(s -> s.split(",", 2))
                        .collect(Collectors.toUnmodifiableMap(tuple -> tuple[0], tuple -> tuple[1]));
            }
        }
    }

    private String joinField(List<String[]> group, int field) {
        return "\"" + group.stream()
                .map(arr -> arr[field].replace("\"", ""))
                .distinct()
                .collect(Collectors.joining()) + "\"";
    }

    // インデックスファイルを作成する
    private File createIndex(String outputPath, List<String> source) throws IOException {
        System.out.print("インデックス作成中...");
        Map<String, Set<Integer>> tokenMap = new HashMap<>();
        // データソースの行ごとで処理する
        IntStream.range(0, source.size()).forEach(lineCount -> {
            String line = source.get(lineCount);
            // [都道府県, 市区町村, 町域]を抽出
            String[] addresses = Arrays.copyOfRange(line.split(","), 6, 9);
            for (String address : addresses) {
                address = address.replace("\"", "");
                // トークン化して、位置情報(行目)をSetにまとめる
                String[] biGramTokens = biGramTokenizer(address);
                for (String token : biGramTokens) {
                    if (!tokenMap.containsKey(token)) tokenMap.put(token, new HashSet<>());
                    Set<Integer> lines = tokenMap.get(token);
                    lines.add(lineCount);
                }
            }
        });

        File indexFile = new File(outputPath);
        try (FileWriter filewriter = new FileWriter(indexFile, charset)) {
            tokenMap.forEach((key, value) -> {
                String l = key + ","
                        + value.stream()
                        .map(i -> i.toString())
                        .collect(Collectors.joining(",")) + System.lineSeparator();
                try {
                    filewriter.append(l);
                } catch (IOException e) {
                    System.out.println("failed when saving index: [" + l + "]");
                    e.printStackTrace();
                }
            });
        }
        System.out.println("Done");
        return indexFile;
    }

    // bi-gramトークン化処理
    private static String[] biGramTokenizer(String str) {
        int tokenCount = str.length() - 1;
        String[] tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = str.substring(i, i+2);
        }
        return tokens;
    }

    private Map<Integer, Integer> searchTokens(String[] tokens) {
        // key: 位置情報(行目)
        // value: 該当行目のウェイト(回数)
        Map<String, Integer> results = new HashMap<>();

        // TODO: Stream化
        for (String searchToken : tokens) {
            if (!this.indexes.containsKey(searchToken)) continue;
            Arrays.stream(this.indexes.get(searchToken).split(","))
                    .forEach(lineNo -> {
                        // ウェイトを更新
                        results.put(lineNo, results.containsKey(lineNo) ? results.get(lineNo) + 1 : 1);
                    });
        }

        return results.entrySet().stream().
                collect(Collectors.toMap(entry -> Integer.valueOf(entry.getKey()), Map.Entry::getValue));
    }
}
