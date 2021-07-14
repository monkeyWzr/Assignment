package com.monkeywzr;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("データソースを指定ください");
        }
        String sourceFilePath = args[0];
        AddressSearcher searcher = null;
        try {
            searcher = new AddressSearcher(sourceFilePath);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("アドレスを入力ください:");
            String searchStr = sc.nextLine();
            if (searchStr.isBlank()) return;
            List<String> results = searcher.search(searchStr);
            System.out.println("----- " + results.size() + "件 -----------");
            results.forEach(System.out::println);
            System.out.println("----- " + results.size() + "件 -----------");
            System.out.println();
        }
    }
}
