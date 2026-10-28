package com.work.mautonlaundry.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


public class InterviewPractice {
    public static void main(String[] args) {
        String word = "My business is great";
        String palCheack = "madam";
        String reverse = reverseWords(word);
        String rearrange = rearrangeWords(word);
        boolean isPalindrome = palCheaker(palCheack);
//        String[] newArray = {"2", "4", "2"};
        Set<String> duplicateCheack = duplicates(new String[]{"2", "4", "2", "5", "6", "7", "8", "9", "4"});


        System.out.println("Original: " + word);
        System.out.println("Rearranged: " + rearrange);
        System.out.println("Reversed: " + reverse);
        System.out.println("Is Palindrome: " + isPalindrome);
        System.out.println("These are duplicates: " + duplicateCheack);

    }

    public static String rearrangeWords(String word){
        String[] words = word.split(" ");
        StringBuilder reverse = new StringBuilder();

        for(int i = words.length -1; i>=0; i--){
            reverse.append(words[i]);
            if(i != 0){
                reverse.append(" ");
            }
        }
        return reverse.toString();
    }

    public static String reverseWords(String word){
        String[] words = word.split(" ");
        StringBuilder reverse = new StringBuilder();

        for(int i = words.length -1; i>=0; i--){
            char[] singleWord = words[i].toCharArray();
            StringBuilder reverseWord = new StringBuilder();
            for (int j = singleWord.length -1; j >= 0; j--) {
                reverseWord.append(singleWord[j]);
            }
            reverse.append(reverseWord.toString());
            if(i != 0){
                reverse.append(" ");
            }
        }
        return reverse.toString();
    }

    public static boolean palCheaker(String word){
        StringBuilder palCheack = new StringBuilder();
        String reversedWord = palCheack.append(word).reverse().toString();
        return reversedWord.equalsIgnoreCase(word);
    }

    public static Set<String> duplicates(String[] array){
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (String item : array) {
            if (!seen.add(item)) {  // add() returns false if already present
                duplicates.add(item);
            }
        }
        return duplicates;
    }

    public static long wordFrequency(String word, String sentence){
        String[] words = sentence.split(" ");
        return Arrays.stream(words)
                .filter(w -> w.equalsIgnoreCase(word))
                .count();
    }

    public static Map<String, Long> wordFrequencyMap(String sentence){
        String[] words = sentence.split(" ");
        return Arrays.stream(words)
                .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));
    }
}
