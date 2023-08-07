/**
 * @Copyright (C), HITSZ
 * @Author Maohua Lyu, Wensheng, gan
 * @Date Created in 9:41 2022/10/9.
 * @Version 1.5
 * @Description
 */


import java.io.IOException;


public class Algo {
    public static void main(String [] arg) throws IOException {
        // THIS IS THE INPUT FILE:
        String[] filenames = new String[]{"Scalability_10K.txt", "Scalability_20K.txt"
                , "BIBLE.txt", "kosarak10k.txt", "Leviathan.txt", "SIGN.txt"};

        String filename = filenames[4];
        int index = 6;

        String input = "./input/" + filename;
        // THIS IS THE OUTPUT FILE PATH FOR SAVING HIGH UTILITY SEQUENTIAL RULES


        System.out.println("=========================== processing " + filename + "===========================");
        // THIS IS THE MINIMUM CONFIDENCE PARAMETER  (e.g. 60 %)
        double minconf = 0.6;

        // THIS IS THE MINIMUM UTILITY PARAMETER  (e.g. 30 $ )

//        double[] minutils = {1200, 1250, 1300, 1350, 1400, 1450, 1500};       //10k
//        double[] minutils = {2100, 2200, 2300, 2400, 2500, 2600, 2700};       //20k
//        double[] minutils = {12000, 15000, 18000, 21000, 24000, 27000, 30000};    //BIBLE
        double[] minutils = {17500, 19000, 20500, 22000, 23500, 25000, 26500};  //kosarak10k
//        double[] minutils = {2000, 2500, 3000, 3500, 4000, 4500, 5000};      //Leviathan
//        double[] minutils = {18000, 20000, 22000, 24000, 26000, 28000, 30000};    //SIGN
        double minutil = minutils[index];


        // THESE ARE ADDITIONAL PARAMETERS
        // THE FIRST PARAMETER IS A CONSTRAINT ON THE MAXIMUM NUMBER OF ITEMS ON THE LEFT SIDE OF RULES
        // For example, we don't want to find rules with more than 5 items in their left side
        int maxAntecedentSize = 5;
        // THE SECOND PARAMETER IS A CONSTRAINT ON THE MAXIMUM NUMBER OF ITEMS ON THE RIGHT SIDE OF RULES
        // For example, we don't want to find rules with more than 5 items in their right side
        int maxConsequentSize = 5;

        // This parameter let the user specify how many sequences from the input file should be used.
        // For example, it could be used to read only the first 1000 sequences of an input file
        int maximumSequenceCount = Integer.MAX_VALUE;
        String output = "./output/" + filename.substring(0, filename.length() - 4) + minutils[index] + ".txt";

        // This creates the algorithm and run it
        // Results will be output to the file
        AlgoHUSRM algo = new AlgoHUSRM();
        algo.runAlgorithm(input, output, minconf, minutil, maxAntecedentSize, maxConsequentSize, maximumSequenceCount);
        // print statistics
        algo.printStats();

//        Comparators comparators = new Comparators();
//        comparators.runAlgorithm(input, output, minconf, minutil, maxAntecedentSize, maxConsequentSize, maximumSequenceCount);
//        comparators.printStats();
    }
}
