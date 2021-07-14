package com.monkeywzr;

import java.util.List;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        AddressSearcher searcher = new AddressSearcher("KEN_ALL.CSV");
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("アドレスを入力ください:");
            String searchStr = sc.nextLine();
            if (searchStr.isBlank()) return;
            List<String> results = searcher.search(searchStr);
            System.out.println("----- " + results.size() + "件 -----------");
            results.forEach(System.out::println);
            System.out.println("------------------------");
        }
    }
}
