/**
 * @Copyright (C), HITSZ
 * @Author Maohua Lyu, Wensheng, gan
 * @Date Created in 9:41 2022/10/9.
 * @Version 1.5
 * @Description
 */


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class Comparators {
    /** start time of latest execution */
    long timeStart = 0;
    /** end time of latest execution */
    long timeEnd = 0;
    /**  number of rules generated */
    int ruleCount;

    /** number of utility table
     * it is equivalent to the candidate rule's number in the paper of TotalSR
     * */
    long itemTableCount = 0;

    private long expandTimes = 0;



    // parameters ***/
    /** minimum confidence **/
    double minConfidence;

    /** minimum support */
    double minutil;

    int maxItem = -1;
    /** this is the sequence database */
    SequenceDatabaseWithUtility database;

    /** this buffered writer is used to write the output file */
    BufferedWriter writer = null;

    /** this is a map where the KEY is an item and the VALUE is the list of sequences
     /* containing the item. */
    private Map<Integer, ListSequenceIDs> mapItemSequences;


    /** this is a contrainst on the maximum number of item that the left side of a rule should
     /* contain */
    private int maxSizeAntecedent;

    /** this is a contrainst on the maximum number of item that the right side of a rule should
     /* contain */
    private int maxSizeConsequent;


    private int maximumRemoveTimes = Integer.MAX_VALUE;

    private double[][] UPSL;
    private List<HashMap<Integer, Integer>> indicex = new ArrayList<>();
    private HashMap<Integer, Integer> lastIndexOfSeq = new HashMap<>();   // key: seq_number, value: index of the last item in this sequence.

//    private long tableSize = 0;

    /** this variable is used to activate the debug mode.  When this mode is activated
     /* some additional information about the algorithm will be shown in the console for
     /* debugging **/
    final boolean DEBUG = false;

    ////// ================ STRATEGIES ===============================
    // Various strategies have been used to improve the performance of HUSRM.
    // The following boolean values are used to deactivate these strategies.

    /** Strategy 1: remove items with a sequence estimated utility < minutil */
    private boolean deactivateStrategy1 = false;

    /** Strategy 2: remove rules contains two items a--> b with a sequence estimated utility < minutil */
    private boolean deactivateStrategy2 = false;

    private boolean RSUPruning = false;
    private boolean RSPEU = true;


    /**
     * Default constructor
     */
    public Comparators() {
    }

    /**
     * This is a structure to store some estimated utility and a list of sequence ids.
     * It will be use in the code for storing the estimated utility of a rule and the list
     * of sequence ids where the rule appears.
     */
    public class EstimatedUtilityAndSequences{
        // an estimated profit value
        Double utility = 0d;
        // a list of sequence ids
        List<Integer> sequenceIds = new ArrayList<Integer>();
    }

    /**
     * The main method to run the algorithm
     *
     * @param input an input file
     * @param output an output file
     * @param minConfidence the minimum confidence threshold
     * @param minutil the minimum utility threshold
     * @param maxConsequentSize a constraint on the maximum number of items that the right side of a rule should contain
     * @param maxAntecedentSize a constraint on the maximum number of items that the left side of a rule should contain
     * @param maximumNumberOfSequences the maximum number of sequences to be used
     * @exception IOException if error reading/writing files
     */
    //@SuppressWarnings("unused")
    public void runAlgorithm(String input, String output,
                             double minConfidence, double minutil, int maxAntecedentSize, int maxConsequentSize,
                             int maximumNumberOfSequences) throws IOException {

        // save the minimum confidence parameter
        this.minConfidence = minConfidence;

        // save the constraints on the maximum size of left/right side of the rules
        this.maxSizeAntecedent  = maxAntecedentSize;
        this.maxSizeConsequent  = maxConsequentSize;

        // reinitialize the number of rules found
        ruleCount = 0;
        this.minutil = minutil;

        // if the database was not loaded, then load it.
        if (database == null) {
            try {
                database = new SequenceDatabaseWithUtility();
                database.loadFile(input, maximumNumberOfSequences);
                maxItem = database.getMaxItem();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // if in debug mode, we print the database to the console
        if(DEBUG){
            System.out.println("Without SEU");
            database.print();
        }

        // We reset the tool for calculating the maximum memory usage
        MemoryLogger.getInstance().reset();

        // we prepare the object for writing the output file
        writer = new BufferedWriter(new FileWriter(output));

        // if minutil is 0, set it to 1 to avoid generating
        // all rules
        this.minutil = minutil;
        if (this.minutil == 0) {
            this.minutil = 0.001;
        }

        // save the start time
        timeStart = System.currentTimeMillis(); // for stats

        int removeCount = 0;
        int removeTimes = 0;



        // if this strategy has not been deactivated
        if(!deactivateStrategy1){
            maxItem = -1;

            // FIRST STEP: We will calculate the estimated profit of each single item
            // This map will store pairs of (key: item   value: estimated profit of the item)
            Map<Integer, Double> mapItemEstimatedUtility = new HashMap<Integer, Double>();

            // We read the database.
            // For each sequence
            // 获取数据库中的每一条序列
            for (SequenceWithUtility sequence : database.getSequences()) {

                // for each itemset in that sequence
                for (List<Integer> itemset : sequence.getItemsets()) {

                    // for each item
                    for (Integer item : itemset) {
                        // get the current sequence estimated utility of that item
                        Double estimatedUtility = mapItemEstimatedUtility.get(item);
                        maxItem = Math.max(maxItem, item);
                        // if we did not see that item yet
                        if  (estimatedUtility == null)    {
                            // then its estimated utility of that item until now is the
                            // utility of that sequence
                            estimatedUtility = sequence.exactUtility;

                        } else {
                            // otherwise, it is not the first time that we saw that item
                            // so we add the utility of that sequence to the sequence
                            // estimated utility f that item
                            estimatedUtility = estimatedUtility + sequence.exactUtility;
                        }

                        // update the estimated utility of that item in the map
                        mapItemEstimatedUtility.put(item, estimatedUtility);

                    }
                }
            }

            // use SEU strategy
            // we create an iterator to loop over all items
            Iterator<Map.Entry<Integer, Double>> iterator = mapItemEstimatedUtility.entrySet().iterator();
            // for each item
            while (iterator.hasNext()) {

                // we obtain the entry in the map
                Map.Entry<java.lang.Integer, java.lang.Double> entryMapItemEstimatedUtility
                        = (Map.Entry<java.lang.Integer, java.lang.Double>) iterator.next();
                Double estimatedUtility = entryMapItemEstimatedUtility.getValue();

                // if the estimated utility of the current item is less than minutil
                if (estimatedUtility < minutil) {
                    removeCount++;
                    // we remove the item from the map
                    iterator.remove();
                }
            }

            double removeUtility = 0;

            while(removeTimes < maximumRemoveTimes) {
                // If not item can be removed
                if(removeCount == 0) {
                    break;
                }
                // update removeTimes
                removeTimes++;
                removeCount = 0;
                // scan the database again.
                // For each sequence
                Iterator<SequenceWithUtility> iteratorSequence = database.getSequences().iterator();
                while (iteratorSequence.hasNext()) {

                    SequenceWithUtility sequence = iteratorSequence.next();
                    // Initialize to 0
                    removeUtility = 0;

                    // For each itemset
                    Iterator<List<Integer>> iteratorItemset = sequence.getItemsets().iterator();
                    Iterator<List<Double>> iteratorItemsetUtilities = sequence.getUtilities().iterator();
                    while (iteratorItemset.hasNext()) {
                        // the items in that itemset
                        List<java.lang.Integer> itemset = iteratorItemset.next();
                        // the utility values in that itemset
                        List<java.lang.Double> itemsetUtilities = iteratorItemsetUtilities.next();

                        // Create an iterator over each item in that itemset
                        Iterator<Integer> iteratorItem = itemset.iterator();
                        // Create an iterator over utility values in that itemset
                        Iterator<Double> iteratorItemUtility = itemsetUtilities.iterator();

                        // For each item
                        while (iteratorItem.hasNext()) {
                            // get the item
                            Integer item = iteratorItem.next();
                            // get its utility value
                            Double utility = iteratorItemUtility.next();

                            // if the item is unpromising
                            if(mapItemEstimatedUtility.get(item) == null){

                                // remove the item
                                iteratorItem.remove();
                                // remove its utility value
                                iteratorItemUtility.remove();
                                // subtract the item utility value from the sequence utility.
                                sequence.exactUtility -= utility;
                                // update removeUtility
                                removeUtility += utility;
                            }
                        }

                        // If the itemset has become empty, we remove it from the sequence
                        if(itemset.isEmpty()){
                            iteratorItemset.remove();
                            iteratorItemsetUtilities.remove();
                        }
                    }

                    // If the sequence has become empty, we remove the sequences from the database
                    if(sequence.size() == 0 || sequence.size() == 1){
                        iteratorSequence.remove();
                    }else {
                        // update the SEU of all items
                        iteratorItemset = sequence.getItemsets().iterator();
                        while (iteratorItemset.hasNext()) {
                            // the items in that itemset
                            List<java.lang.Integer> itemset = iteratorItemset.next();

                            // Create an iterator over each item in that itemset
                            Iterator<Integer> iteratorItem = itemset.iterator();

                            // For each item
                            while (iteratorItem.hasNext()) {
                                // get the item
                                Integer item = iteratorItem.next();
                                // Update the SEU of this item and determine if it should be removed
                                if(mapItemEstimatedUtility.get(item) - removeUtility < minutil) {
                                    removeCount++;
                                    mapItemEstimatedUtility.remove(item);
                                }else {
                                    mapItemEstimatedUtility.put(item, mapItemEstimatedUtility.get(item) - removeUtility);
                                }
                            }
                        }
                    }
                }
            }
        }

        if(DEBUG){
            System.out.println("After SEU");
            database.print();
        }


        // We create a map to store for each item, the list of sequences containing the item
        // Key: an item   Value:  the list of sequences containing the item
        mapItemSequences = new HashMap<Integer, ListSequenceIDs>();

        UPSL = new double[database.getSequences().size()][database.getLargestLength() + 1];
        if (DEBUG){
            System.out.println("database.length:" + database.getSequences().size() + "\tseq.largestLength: " + database.getLargestLength());
        }
        // For each sequence
        for (int i=0; i < database.getSequences().size(); i++){
            SequenceWithUtility sequence = database.getSequences().get(i);
            HashMap<Integer, Integer> itemPosition = new HashMap<>();
            int index = 0;
            // For each itemset
            // List<Integer> itemset : sequence.getItemsets()
            for (int j = 0; j < sequence.getItemsets().size(); j++) {
                List<Integer> itemset = sequence.getItemsets().get(j);

                // For each item
                int itemSetIndex = 0;
                for (Integer item : itemset) {
                    // Get the list of identifiers of sequence containing that item
                    ListSequenceIDs numerosSequenceItem = mapItemSequences.get(item);
                    UPSL[i][index + 1] = UPSL[i][index] + sequence.getUtilities().get(j).get(itemSetIndex ++);
                    itemPosition.put(item, ++index);
                    // If the list does not exist, we will create it
                    if(numerosSequenceItem == null){
                        numerosSequenceItem = new ListSequenceIDsArrayList();
                        // we add the list in the map for that item
                        mapItemSequences.put(item, numerosSequenceItem);
                    }
                    // finally we add the current sequence ids to the list of sequences ids of the current
                    // item
                    numerosSequenceItem.addSequenceID(i, j);
                }
            }
            lastIndexOfSeq.put(i, index);
            indicex.add(itemPosition);  // indicex[sid] = <key: item, value: index>
        }

        if(DEBUG) {
            for (int i = 0; i < UPSL.length; i++)
                System.out.println(Arrays.toString(UPSL[i]));
        }

        // We create a map of map to store the estimated utility and list of sequences ids for
        // each rule of two items (e.g. a -> b  ).
        // The key of the first map: the item "a" in the left side of the rule
        // The key of the second map:  the item "b" in the right side of the rule
        // The value in the second map:  the estimated utility of the rule and sequence ids for that rule
        Map<Integer,Map<Integer, EstimatedUtilityAndSequences>> mapItemItemEstimatedUtility = new HashMap<Integer,Map<Integer, EstimatedUtilityAndSequences>>();

        // we do not use SEU to pruning rule.


        // For each sequence
        for (int z = 0; z < database.getSequences().size(); z++) {
            SequenceWithUtility sequence = database.getSequences().get(z);

            // For each itemset i
            for (int i=0; i < sequence.getItemsets().size(); i++) {

                // get the itemset
                List<Integer> itemset = sequence.getItemsets().get(i);

                // For each item  X
                for (int j=0; j < itemset.size(); j++) {
                    Integer itemX = itemset.get(j);

                    // For each item Y occuring after X,
                    // that is in the itemsets I+1, I+2 ....
                    for (int k=i+1; k < sequence.getItemsets().size(); k++) {
                        //  for a given itemset K
                        List<Integer> itemsetK = sequence.getItemsets().get(k);
                        // for an item Y
                        for(Integer itemY: itemsetK){

                            // We will update the estimated profit of the rule X --> Y
                            // by adding the sequence utility of that sequence to the
                            // sequence estimated utility of that rule

                            // Get the map for item X
                            Map<Integer, EstimatedUtilityAndSequences> mapXItemUtility = mapItemItemEstimatedUtility.get(itemX);

                            // If we never saw X before
                            if(mapXItemUtility == null){
                                // we create a map for X
                                mapXItemUtility = new HashMap<Integer, EstimatedUtilityAndSequences>();
                                mapItemItemEstimatedUtility.put(itemX, mapXItemUtility);

                                // Then we create a structure for storing the estimated utility of X ->Y
                                EstimatedUtilityAndSequences structure = new EstimatedUtilityAndSequences();
                                structure.utility = sequence.exactUtility; // the current sequence utility
                                structure.sequenceIds.add(z); // the sequence id
                                // add it in the map for X -> Y
                                mapXItemUtility.put(itemY, structure);
                            }
                            else{
                                // in the case were we saw X before.
                                // We get its structure for storing the estimated utility of X -> Y
                                EstimatedUtilityAndSequences structure = mapXItemUtility.get(itemY);
                                // if we never saw X ->Y
                                if(structure == null){

                                    // Then we create a structure for storing the estimated utility of X ->Y
                                    structure = new EstimatedUtilityAndSequences();
                                    structure.utility = sequence.exactUtility; // the current sequence utility
                                    structure.sequenceIds.add(z); // the sequence id

                                    // add it in the map for X -> Y
                                    mapXItemUtility.put(itemY, structure);
                                }else{
                                    // if we saw X -> Y before
                                    // We add the sequence utility to the utility of that rule
                                    structure.utility = structure.utility + sequence.exactUtility;
                                    // We add the sequence ids to the list of sequence ids of that rule.
                                    structure.sequenceIds.add(z);
                                }
                            }
                        }
                    }
                }
            }
        }


        // For each entry in the map
        for(Map.Entry<Integer, Map<Integer, EstimatedUtilityAndSequences>> mapI : mapItemItemEstimatedUtility.entrySet()){

            // An entry represents an item "i" (the key) and some maps (value)
            // We will loop over the entries of the secondary map of "i" (value)
            // to remove all rules of the form i -> j where the estimated utility
            // is lower than minutil

            // Create an iterator
            Iterator<Map.Entry<Integer, EstimatedUtilityAndSequences>> iterEntry = 	mapI.getValue().entrySet().iterator();

            // loop over the map
            while (iterEntry.hasNext()) {
                // We consider each entry j and the estimated utility of i-> j
                Map.Entry<java.lang.Integer, EstimatedUtilityAndSequences> entry = (Map.Entry<java.lang.Integer, EstimatedUtilityAndSequences>) iterEntry
                        .next();
                // if the estimated profit of i -> j is lower than minutil
                // we remove that rule because no larger rule containing that rule
                // can have a estimated utility higher or equal to minutil.
                if(entry.getValue().utility < minutil){
                    // we only do that if the user did not deactivate strategy 2
                    if(!deactivateStrategy2){
                        iterEntry.remove();
                    }
                }
            }
        }


        // For each rule X --> Y
        for(Map.Entry<Integer, Map<Integer, EstimatedUtilityAndSequences>> entryX : mapItemItemEstimatedUtility.entrySet()){
            // Get the item X
            Integer itemX = entryX.getKey();

            // Get the list of sequence ids containing the item X
            ListSequenceIDs sequenceIDsX =  mapItemSequences.get(itemX);
            List<Integer> sequencesIDsX = sequenceIDsX.getSequences();
            Map<List<Integer>, Map<Integer, Integer>> prefix = new HashMap<>();
            List<Integer> antecedent = new ArrayList<>();
            antecedent.add(itemX);

            // Get the support of item X
            double supportX = sequencesIDsX.size();

            // For each Y
            for(Map.Entry<Integer, EstimatedUtilityAndSequences> entryYUtility : entryX.getValue().entrySet()){
                Integer itemY = entryYUtility.getKey();
                Map<Integer, Integer> Positions = new HashMap<>(sequenceIDsX.getSeqAndItemsetPos());
                // Get the estimated utility and list of sequences ids for the rule X -> Y
                EstimatedUtilityAndSequences structure = entryYUtility.getValue();
                List<Integer> sequencesIDsXY = structure.sequenceIds;

                // Get the support of the rule X ->Y
                double supportXY = sequencesIDsXY.size();

                // We create the utility table of rule X -> Y
                // 这里的sid存的是前件的id
                UtilityTable table = new UtilityTable();

                // We will scan each sequence to fill the utility table
                // and update the other variable to calculate the confidence of the rule.

                // for each sequence containing X -> Y
                for(Integer numeroSequence : sequencesIDsXY){
                    // Get the sequence
                    SequenceWithUtility sequence = database.getSequences().get(numeroSequence);
                    Positions.remove(numeroSequence);

                    // Create a new element in the table
                    ElementOfTable element = new ElementOfTable(numeroSequence);
                    element.utility = 0;

                    // we reset position alpha and beta

                    // (1) We will scan the sequence from left to right to find X
                    // and stop at the first position ALPHA where X has been seen completely.
                    // At the same time, we will add the utility of items in X.

                    // For each itemset I
                    double utilityXPositionIJ = 0;
                    boolean found = false;
                    for (int i = 0; i < sequence.getItemsets().size(); i++) {
                        // get the itemset I
                        List<Integer> itemset = sequence.getItemsets().get(i);

                        // For each item J
                        for (int j = 0; j < itemset.size(); j++) {
                            Integer itemIJ = itemset.get(j);

                            // if we found the item X
                            if (itemX.equals(itemIJ)) {
                                // we get its utility
                                utilityXPositionIJ = sequence.getUtilities().get(i).get(j);
                                // we add it to the exact utility in the current utility table element

                                // Stop and remember that position
                                element.positionAlphaItemset = i;
                                // remember the position ALPHA (which in this case means where the item in
                                // the right side
                                // of a rule was found)

                                // since we found j, we don't need to continue this loop since we assume
                                // that an item do not occur more than once per sequence
                                found = true;
                                break;
                            }
                        }
                    }

                    // If X does not appear, we don't do the following steps
                    if(element.positionAlphaItemset == -1){
                        continue;
                    }

                    // (2) Now we will scan the sequence from right to left to find
                    //  Y and stop if we find it. That position where we find it will be called beta.
                    // At the same time as we scan the sequence, we will add the utility of items in Y

                    // for each itemset starting from the last one until itemset alpha+1
                    loop2:	for (int i = sequence.getItemsets().size()-1;
                                   i >  element.positionAlphaItemset ; i--) {
                        // get the current itemset
                        List<Integer> itemset = sequence.getItemsets().get(i);

                        // for each item J in that itemset
                        for (int j = itemset.size() -1; j >= 0; j--) {
                            // get the item J
                            Integer itemIJ = itemset.get(j);

                            // if that item is Y
                            if(itemY.equals(itemIJ))
                            {
                                // we add Y's profit to the exact utility of the current element
                                double profitYPositionIJ = sequence.getUtilities().get(i).get(j);
                                element.utility += utilityXPositionIJ;
                                element.utility += profitYPositionIJ;

                                // we stop and remember that we stopped at the i-th itemset
                                // we will call this position "beta".
                                element.positionBetaItemset = i;
                                element.positionGamaItemset = i;

                                break loop2;
                            }

                        }
                    }
                    if (found)
                    {
                        HashMap<Integer, Integer> idxS = indicex.get(numeroSequence);
                        int idx0 = 0, idx1 = 0, idx2 = 0;

                        idx0 = idxS.get(itemX);
                        idx1 = idxS.get(sequence.getItemsets().get(element.positionBetaItemset).get(0));
                        idx2 = idxS.get(itemY);
                        element.utilityLeft += UPSL[numeroSequence][idx1 - 1]
                                - UPSL[numeroSequence][idx0];
                        element.utilityRight += UPSL[numeroSequence][lastIndexOfSeq.get(numeroSequence)]
                                - UPSL[numeroSequence][idx2];
                    }

                    // Finally, we add the element of this sequence to the utility table of X->Y
                    table.addElement(element);
                }

                prefix.put(antecedent, Positions);
                // We calculate the confidence of X -> Y
                double confidence = (supportXY / supportX);

                double conditionExpandLeft;
                double conditionExpandRight;

                conditionExpandLeft = table.getTotalUtilityLeft() + table.getTotalUtility() + table.getTotalUtilityRight();
                conditionExpandRight = table.getTotalUtility() + table.getTotalUtilityRight();


                // create the rule antecedent and consequence
                List<Integer> consequent = new ArrayList<>();
                consequent.add(itemY);


                // if high utility with ENOUGH  confidence
                if((table.getTotalUtility() >= minutil) && confidence >= minConfidence){
                    ruleCount++;
                    // we output the rule
                }


                // if the left side size is less than the maximum size, we will try to expand the rule
                if(conditionExpandLeft >= minutil  && maxAntecedentSize > 1) {
                    expandTimes++;
                    expandLeft(prefix, table, antecedent, consequent, 1, 1);
                }


//                 if the right side size is less than the maximum size, we will try to expand the rule
                if(conditionExpandRight >= minutil && maxConsequentSize > 1)
                {
                    expandTimes++;
                    expandFirstRight(supportX, table, antecedent, consequent,1, 1);
                }
            }
        }


        //We will check the current memory usage
        MemoryLogger.getInstance().checkMemory();

        // save end time
        timeEnd = System.currentTimeMillis();

        saveRule(minutil, ruleCount, (double)(timeEnd - timeStart) / 1000, MemoryLogger.getInstance().getMaxMemory(), expandTimes, itemTableCount);

        // close the file
        writer.close();

        // after the algorithm ends, we don't need a reference to the database
        // anymore.
        database = null;
    }

    /**
     * This method save a rule to the output file
     * @param antecedent the left side of the rule
     * @param consequent the right side of the rule
     * @param utility the rule utility
     * @param support the rule support
     * @param confidence the rule confidence
     * @throws IOException if an error occurs when writing to file
     */
    private void saveRule(List<Integer> antecedent, List<Integer> consequent,
                          double utility, double support, double confidence) throws IOException {

        // increase the number of rule found

        // create a string buffer
        StringBuilder buffer = new StringBuilder();

        // write the left side of the rule (the antecedent)
        buffer.append("<{");
        for (int i = 0; i < antecedent.size(); i++) {
            if (antecedent.get(i) == -1) {
                //buffer.deleteCharAt(buffer.length() - 1);
                buffer.append("}, {");
            } else {
                buffer.append(antecedent.get(i)).append(",");
            }
        }
        buffer.append("}>");
        // write separator
        buffer.append("	--> <{");

        // write the right side of the rule (the consequent)
        for (int i = 0; i < consequent.size(); i++) {
            if (consequent.get(i) == -1) {
                buffer.append("}, {");
            } else {
                buffer.append(consequent.get(i)).append(",");
            }
        }
        buffer.append("}>");
        // write support
        buffer.append("\t#SUP: ");
        buffer.append(support);
        // write confidence
        buffer.append("\t#CONF: ");
        buffer.append(confidence);
        buffer.append("\t#UTIL: ");
        buffer.append(utility);
        writer.write(buffer.toString());
        writer.newLine();

        //if we are in debug mode, we will automatically check that the utility, confidence and support
        // are correct to ensure that there is no bug.
    }


    private void saveRule(double minutil, long ruleCount, double runTime, double maxMemory, long candidateCount, long itemTableCount) throws IOException {


        // create a string buffer
        StringBuilder buffer = new StringBuilder();

        buffer.append("\tTotalSRPlus");
        // write the left side of the rule (the antecedent)
        buffer.append("\n\tminutil: ").append(minutil);
        buffer.append("\n\tSequential rules count: ").append(ruleCount);
        buffer.append("\n\tTotal time : ").append(runTime).append(" s");
        buffer.append("\n\tMax memory (mb) : ").append(maxMemory);
        buffer.append("\n\texpand times count: ").append(candidateCount);
        buffer.append("\n\tutility table count: ").append(itemTableCount);

        writer.write(buffer.toString());
        writer.newLine();

        //if we are in debug mode, we will automatically check that the utility, confidence and support
        // are correct to ensure that there is no bug.
    }

    private void expandLeft(Map<List<Integer>, Map<Integer, Integer>> prefix ,UtilityTable table, List<Integer> antecedent,
                            List<Integer> consequent, int antecedentLength, int consequentLength) throws IOException {

        if (DEBUG) {
            System.out.print(antecedent);
            System.out.print("-->");
            System.out.print(consequent);
            System.out.println("    LPEU: " + table.totalUtilityLeft + "  RPEU: " + table.totalUtilityRight);
        }

        // We first find the largest item in the left side and right side of the rule

        int lastItemInAntecedent = antecedent.get(antecedent.size() - 1);
        int itemInConsequent = consequent.get(0);

        // We create a new map where we will build the utility table for the new rules that
        // will be created by adding an item to the current rule.
        // Key: an item appended to the rule     Value: the utility-table of the corresponding new rule

        Map<Integer, UtilityTable> mapItemsTables = new HashMap<Integer, UtilityTable>();
        Map<Integer, Double> RSUTable = new HashMap<>();
        Map<Integer, Double> LERSPEUTable = new HashMap<>();
        Map<Integer, Double> exactUtility = new HashMap<>();

//		// for each sequence containing the original rule (according to its utility table)
        Map<Integer, Map<Integer, Integer>> itemToPositions = new HashMap<>();
        double[] UPS;
        double totalUtility = table.totalUtility;
        double totalUtilityLeft = table.totalUtilityLeft;
        double totalUtilityRight = table.totalUtilityRight;
        for(ElementOfTable element : table.elements){
            if (RSUPruning || RSPEU) {
                totalUtility -= element.utility;
                totalUtilityLeft -= element.utilityLeft;
                totalUtilityRight -= element.utilityRight;
            }
            UPS = UPSL[element.numeroSequence];

            // Optimisation: if the "rutil" is 0 for that rule in that sequence,
            // we do not need to scan this sequence.
            // right utility = 0 means that sequence cannot expand for larger rule of consequent.

            // Get the sequence
            SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);

            HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);
            int idx3 = -1;
            if (element.positionBetaItemset != -1)
                idx3 = idxs.get(sequence.getItemsets().get(element.positionBetaItemset).get(0));  // get the index of the first item in consequent in the sequence.
            //============================================================
            // case 1 and case 2
            // Case 1: for each itemset in BETA or AFTER BETA.....

            // For each itemset after alpha:
            for(int i = element.positionAlphaItemset; i < sequence.size(); i++){
                // get the itemset
                List<Integer> itemsetI = sequence.getItemsets().get(i);

                // For each item
                for(int j = 0; j < itemsetI.size(); j++){
                    int itemJ = itemsetI.get(j);

                    // Check if the item is greater than items in the antecedent of the rule
                    // according to the lexicographical order
                    if(i == element.positionAlphaItemset && itemJ <= lastItemInAntecedent){
                        // if not, then we continue because that item cannot be added to the rule
                        continue;
                    }

                    if (itemJ == itemInConsequent) continue;
                    // ======= Otherwise, we need to update the utility table of the item ====================

                    int idx2 = idxs.get(itemJ % (maxItem + 1));
                    // Get the utility table of the item
                    if (i == element.positionAlphaItemset) {
                        itemJ += maxItem + 1;
                    }

                    if (RSUPruning) {
                        if (i < element.positionBetaItemset) {
                            if (RSUTable.get(itemJ) == null) {
                                RSUTable.put(itemJ, element.utilityLeft + element.utility + element.utilityRight);
                            } else {
                                RSUTable.put(itemJ, element.utilityLeft + element.utility + element.utilityRight + RSUTable.get(itemJ));
                            }
                        } else {
                            // can be deleted
                            if (RSUTable.get(itemJ) == null) {
                                RSUTable.put(itemJ, 0.0);
                            } else {
                                RSUTable.put(itemJ, RSUTable.get(itemJ));
                            }
                        }
                    }

                    if (RSPEU) {
                        if (i < element.positionBetaItemset) {
                            double utility;
                            if (idx2 + 1 == idx3)   // there is no item between antecedent and consequent.
                                utility = element.utilityLeft;
                            else
                                utility = (UPS[idx3 - 1] - UPS[idx2 - 1]);  // the sum of utility of the items that can be extended into antecedent.
                            if (LERSPEUTable.get(itemJ) == null) {
                                LERSPEUTable.put(itemJ, utility + element.utility + element.utilityRight);
                            }  else {
                                LERSPEUTable.put(itemJ, utility + element.utility + element.utilityRight + LERSPEUTable.get(itemJ));
                            }
                        } else {
                            if (LERSPEUTable.get(itemJ) == null) {
                                LERSPEUTable.put(itemJ, 0.0);
                            } else {
                                LERSPEUTable.put(itemJ, LERSPEUTable.get(itemJ));
                            }
                        }
                    }

                    UtilityTable tableItemJ = mapItemsTables.get(itemJ);    // get the utility table of itemJ
                    if (RSUPruning) {
                        if (RSUTable.get(itemJ) + totalUtility + totalUtilityRight + totalUtilityLeft < minutil) {
                            if (tableItemJ != null) {
                                mapItemsTables.remove(itemJ);
                            }
                            if (DEBUG) {
                                System.out.println("\n\t  LOW UTILITY SEQ. RULE: " + antecedent +
                                        " --> " + consequent  + "," + itemJ % (maxItem + 1) + "   utility " + totalUtility
                                        + " rsu : " + RSUTable.get(itemJ)
                                        + " LPEU : " + totalUtilityLeft
                                        + " RPEU : " + totalUtilityRight
                                );
                            }
                            continue;
                        }
                    }

                    if (RSPEU) {
                        if  (LERSPEUTable.get(itemJ) + totalUtility + totalUtilityLeft + totalUtilityRight < minutil) {
                            if (tableItemJ != null) {
                                mapItemsTables.remove(itemJ);
                            }
                            if (DEBUG) {
                                System.out.println("\n\t  LOW UTILITY SEQ. RULE: " + antecedent +
                                        " --> " + consequent  + "," + itemJ % (maxItem + 1) + "   utility " + totalUtility
                                        + " rspeu : " + LERSPEUTable.get(itemJ)
                                        + " LPEU : " + totalUtilityLeft
                                        + " RPEU : " + totalUtilityRight
                                );
                            }
                            continue;
                        }
                    }

                    //==========

                    // We will add a new element (line) in the utility table

                    if (i < element.positionBetaItemset) {
                        if(tableItemJ == null){
                            // if no utility table, we create one
                            tableItemJ = new UtilityTable();
                            mapItemsTables.put(itemJ, tableItemJ);
                        }
                        ElementOfTable newElement = new ElementOfTable(element.numeroSequence);

                        // We will update the utility by adding the utility of item J
                        double profitItemJ = sequence.getUtilities().get(i).get(j);
                        newElement.utility = element.utility + profitItemJ; // rule's utility
                        newElement.utilityRight = element.utilityRight; // REPEU
                        newElement.positionBetaItemset = element.positionBetaItemset;   //
                        newElement.positionAlphaItemset = i;    // new Alpha
                        newElement.positionGamaItemset = element.positionGamaItemset;

                        // Then, we will scan itemsets after the beta position in the sequence
                        // We will subtract the utility of items that are smaller than item J
                        // according to the lexicographical order from "rutil" because they
                        // cannot be added anymore to the new rule.

                        // for each such itemset

                        newElement.utilityLeft = UPS[idx3 - 1] - UPS[idx2];     // new LEPEU
                        // end

                        // Now that we have created the element for that sequence and that new rule
                        // , we will add the utility table of that new rule
                        tableItemJ.addElement(newElement);
                    } else {
                        // this is the itemJ after of between the items in consequent.
                        Map<Integer, Integer> temPosition = itemToPositions.get(itemJ); // <key: item, value: position (itemset's index)>
                        if (temPosition == null)
                            temPosition = new HashMap<>();
                        temPosition.put(element.numeroSequence, i);
                        itemToPositions.put(itemJ, temPosition);
                    }
                }
            }
        }


        itemTableCount += mapItemsTables.size();
        // For each new rule
        Map<List<Integer>, Map<Integer, Integer>> newPrefix = new HashMap<>();      // this is the ART
        for(Map.Entry<Integer, UtilityTable> entryItemTable :  mapItemsTables.entrySet()){
            List<Integer> newAntecedent = new ArrayList<>(antecedent);
            // We get the item and its utility table
            Integer item = entryItemTable.getKey();
            UtilityTable utilityTable = entryItemTable.getValue();


            // We check if we should try to expand its left side
            boolean shouldExpandLeftSide;
            boolean shouldExpandRightSide;

            if (utilityTable.getTotalUtility() == 0)
            {
                // it will not be performed.
                System.out.println("-1");
                continue;
            }

            shouldExpandLeftSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityLeft() + utilityTable.getTotalUtilityRight() >= minutil
                    && antecedentLength + 1 < maxSizeAntecedent;
            shouldExpandRightSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityRight() >= minutil
                    && consequentLength + 1 < maxSizeConsequent;

            // check if the rule is high utility
            boolean isHighUtility = utilityTable.getTotalUtility() >= minutil;

            // We create the consequent for the new rule by appending the new item
            if (shouldExpandLeftSide || shouldExpandRightSide || isHighUtility) {
                Map<Integer, Integer> newPosition = new HashMap<>();
                int newSID = utilityTable.elements.get(0).numeroSequence;       // get the sid in new table
                int newAlpha = utilityTable.elements.get(0).positionAlphaItemset;   // get new Alpha
                for (ElementOfTable element: table.elements){   // in old table, only to get the newSID and newAlpha then to construct the ART.
                    if (element.numeroSequence == newSID){      // this means we find the sequence in both old and new table so that we can ensure which type of extensions.
                        if (newAlpha == element.positionAlphaItemset){      // I-extension
                            newAntecedent.add(item % (maxItem + 1));
                            // <key: item, value: position (itemset's index)>
                            Map<Integer, Integer> itemToPosition = itemToPositions.get(item);
                            if (itemToPosition != null)     // there are
                                newPosition.putAll(itemToPosition);
                            int newItem = item % (maxItem + 1);
                            // the adding item's position
                            Map<Integer, Integer> newItemPosition = mapItemSequences.get(newItem).getSeqAndItemsetPos();
                            // <key: SID, value: position>
                            Map<Integer, Integer> Position = prefix.get(antecedent);
                            for (int seq: Position.keySet()) {
                                if (newItemPosition.containsKey(seq) && Position.get(seq) == newItemPosition.get(seq)) {
                                    newPosition.put(seq, newItemPosition.get(seq));
                                }
                            }
                            // construct the ART
                            newPrefix.put(newAntecedent, newPosition);
                        }
                        else {      // S-extension
                            newAntecedent.add(-1);
                            newAntecedent.add(item % (maxItem + 1));
                            Map<Integer, Integer> itemToPosition = itemToPositions.get(item);
                            if (itemToPosition != null)
                                newPosition.putAll(itemToPosition);
                            int newItem = item % (maxItem + 1);
                            Map<Integer, Integer> newItemPosition = mapItemSequences.get(newItem).getSeqAndItemsetPos();
                            Map<Integer, Integer> Position = prefix.get(antecedent);
                            for (int seq: Position.keySet()) {
                                if (newItemPosition.containsKey(seq) && Position.get(seq) < newItemPosition.get(seq)) {
                                    newPosition.put(seq, newItemPosition.get(seq));
                                }
                            }
                            newPrefix.put(newAntecedent, newPosition);
                        }
                        break;
                    }
                }

                // We calculate the confidence

                double confidence =  utilityTable.getRuleSize() / (newPrefix.get(newAntecedent).size() + utilityTable.elements.size());

                // If the rule is high utility and high confidence
                if(isHighUtility && confidence >= minConfidence){
                    ruleCount++;
                    if(DEBUG){
                        System.out.println("\n\t  HIGH UTILITY SEQ. RULE: " + newAntecedent +
                                " --> " + consequent  + "," + item + "   utility " + utilityTable.totalUtility
                                + " support : " + utilityTable.elements.size()
                                + " confidence : " + confidence);

                        for(ElementOfTable element : utilityTable.elements){
                            System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                    + " \t lutil: " + element.utilityLeft
                                    + " \t rutil: " + element.utilityRight
                                    + " alpha : " + element.positionAlphaItemset
                                    + " beta : " + element.positionBetaItemset);
                        }
                    }
                } else {
                    if (DEBUG) {
                        System.out.println("\n\t  LOW UTILITY RULE: " + newAntecedent +
                                " --> " + consequent + "," + item + "   utility " + utilityTable.totalUtility
                                + " support : " + utilityTable.elements.size()
                                + " confidence : " + confidence);

                        for(ElementOfTable element : utilityTable.elements){
                            System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                    + " \t lutil: " + element.utilityLeft
                                    + " \t rutil: " + element.utilityRight
                                    + " alpha : " + element.positionAlphaItemset
                                    + " beta : " + element.positionBetaItemset);
                        }
                    }
                }


                // If we should try to expand the left side of this rule
                if(shouldExpandLeftSide && utilityTable.getRuleSize() != 0){
                    expandTimes++;
                    expandLeft(newPrefix, utilityTable, newAntecedent, consequent, antecedentLength + 1, consequentLength);
                }


                if (shouldExpandRightSide){
                    expandTimes++;
                    expandFirstRight((newPrefix.get(newAntecedent).size() + utilityTable.elements.size()),
                            utilityTable, newAntecedent, consequent, antecedentLength + 1, consequentLength);
                }
            }
        }
        MemoryLogger.getInstance().checkMemory();
    }


    /**
     * This method is used to create new rule(s) by adding items to the right side of a rule
     * @param table the utility-table of the rule
     * @param antecedent the rule antecedent
     * @param consequent the rule consequent
     * @throws IOException if an error occurs while writing to file
     */
    private void expandFirstRight(double antecedentSupport, UtilityTable table, List<Integer> antecedent,
                                  List<Integer> consequent, int antecedentLength, int consequentLength) throws IOException {
        if (DEBUG) {
            System.out.print(antecedent);
            System.out.print("-->");
            System.out.println(consequent);
        }

        // We first find the largest item on the right side of the rule
        int lastItemInConsequent = consequent.get(consequent.size() - 1);

        // We create a new map where we will build the utility table for the new rules that
        // will be created by adding an item to the current rule.
        // Key: an item appended to the rule     Value: the utility-table of the corresponding new rule
        Map<Integer, rightUtilityTable> mapItemsTables = new HashMap<>();

        Map<Integer, Double> RSUTable = new HashMap<>();
        Map<Integer, Double> RERSPEU = new HashMap<>();
        double[] UPS;
		// for each sequence containing the original rule (according to its utility table)
        for(ElementOfTable element : table.elements){
            if (RSUPruning || RSPEU) {
                table.totalUtilityRight -= element.utilityRight;
                table.totalUtility -= element.utility;
            }


            UPS = UPSL[element.numeroSequence];
            if (element.utilityRight == 0)
                continue;
            // Get the sequence
            SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);
            HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);
            int idx3 = lastIndexOfSeq.get(element.numeroSequence);

            //============================================================
            // case 1 and case 2
            // Case 1: for each itemset in BETA or AFTER BETA.....

            // For each itemset after gama:
            for(int i = element.positionGamaItemset; i < sequence.size(); i++){
                // get the itemset
                List<Integer> itemsetI = sequence.getItemsets().get(i);

                // For each item
                for(int j = 0; j < itemsetI.size(); j++){
                    Integer itemJ = itemsetI.get(j);
                    // Check if the item is greater than items in the consequent of the rule
                    // according to the lexicographical order
                    if(i == element.positionGamaItemset && itemJ <= lastItemInConsequent){
                        // if not, then we continue because that item cannot be added to the rule
                        continue;
                    }
                    // ======= Otherwise, we need to update the utility table of the item ====================

                    int idx2 = idxs.get(itemJ % (maxItem + 1));
                    // Get the utility table of the item
                    if (i == element.positionGamaItemset) {
                        itemJ += maxItem + 1;
                    }
                    if (RSUPruning) {
                        if (RSUTable.get(itemJ) == null) {
                            RSUTable.put(itemJ, element.utilityRight + element.utility);
                        } else {
                            RSUTable.put(itemJ, element.utilityRight + element.utility + RSUTable.get(itemJ));
                        }
                    }

                    if (RSPEU) {
                        double utility;
                        if (idx2 + 1 == idx3)
                            utility = element.utilityRight;
                        else
                            utility = UPS[idx3] - UPS[idx2 - 1];
                        if (RERSPEU.get(itemJ) == null) {
                            RERSPEU.put(itemJ, utility + element.utility);
                        } else {
                            RERSPEU.put(itemJ, utility + element.utility + RERSPEU.get(itemJ));
                        }
                    }

                    rightUtilityTable tableItemJ = mapItemsTables.get(itemJ);            // itemJ = 7

                    if (RSUPruning) {
                        if (RSUTable.get(itemJ) + table.totalUtilityRight + table.totalUtility < minutil) {
                            if (tableItemJ != null) {
                                mapItemsTables.remove(itemJ);
                            }
                            continue;
                        }
                    }

                    if (RSPEU) {
                        if(RERSPEU.get(itemJ) + table.totalUtilityRight + table.totalUtility < minutil) {
                            if (tableItemJ != null) {
                                mapItemsTables.remove(itemJ);
                            }
                            if (DEBUG) {
                                System.out.println("\n\t  LOW UTILITY SEQ. RULE: " + antecedent +
                                        " --> " + consequent  + "," + itemJ % (maxItem + 1) + "   utility " + table.totalUtility
                                        + " rspeu : " + RERSPEU.get(itemJ)
                                        + " RPEU : " + table.totalUtilityRight
                                );
                            }
                            continue;
                        }
                    }


                    if(tableItemJ == null){
                        tableItemJ = new rightUtilityTable();
                        mapItemsTables.put(itemJ, tableItemJ);
                    }

                    //==========

                    // We will add a new element (line) in the utility table

                    ElementOfRightTable newElement = new ElementOfRightTable(element.numeroSequence);

                    // We will update the utility by adding the utility of item J
                    double profitItemJ = sequence.getUtilities().get(i).get(j);
                    newElement.utility = element.utility + profitItemJ;

                    // we will copy the "rutil" value from the original rule
                    newElement.utilityRight = 0;

                    // we will copy the position of Alpha and Beta in that sequences because it
                    // does not change
                    newElement.positionGamaItemset = i;

                    // Then, we will scan itemsets after the beta position in the sequence
                    // We will subtract the utility of items that are smaller than item J
                    // according to the lexicographical order from "rutil" because they
                    // cannot be added anymore to the new rule.

                    // for each such itemset
                    newElement.utilityRight = UPS[idx3] - UPS[idx2];

                    // end

                    // Now that we have created the element for that sequence and that new rule
                    // , we will add the utility table of that new rule
                    tableItemJ.addElement(newElement);
                }
            }
        }


        itemTableCount += mapItemsTables.size();
        // For each new rule
        for(Map.Entry<Integer, rightUtilityTable> entryItemTable :  mapItemsTables.entrySet()){

            // We get the item and its utility table
            Integer item = entryItemTable.getKey();
            rightUtilityTable utilityTable = entryItemTable.getValue();



            // We check if we should try to expand its right side
            boolean shouldExpandRightSide;

            shouldExpandRightSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityRight() >= minutil
                    && consequentLength+1 < maxSizeConsequent;

            // check if the rule is high utility
            boolean isHighUtility = utilityTable.getTotalUtility() >= minutil;

            // We create the consequent for the new rule by appending the new item
            List<Integer> newConsequent = new ArrayList<>(consequent);

            int newSid = utilityTable.elements.get(0).numeroSequence;
            int newGama = utilityTable.elements.get(0).positionGamaItemset;
            for (ElementOfTable ele: table.elements){
                if (ele.numeroSequence == newSid) {
                    if (newGama == ele.positionGamaItemset) {
                        newConsequent.add(item % (maxItem + 1));
                    }else {
                        newConsequent.add(-1);
                        newConsequent.add(item % (maxItem + 1));
                    }
                    break;
                }
            }
            double confidence = utilityTable.getRuleSize() / (double) antecedentSupport;

            // If the rule is high utility and high confidence
            if(isHighUtility && confidence >= minConfidence){
                ruleCount++;
                if(DEBUG){
                    System.out.println("\n\t  HIGH UTILITY SEQ. RULE: " + antecedent +
                            " --> " + consequent  + "," + item + "   utility " + utilityTable.totalUtility
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for(ElementOfRightTable element : utilityTable.elements){
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                }
            } else {
                if (DEBUG) {
                    System.out.println("\n\t  LOW UTILITY RULE: " + antecedent +
                            " --> " + consequent + "," + item + "   utility " + utilityTable.totalUtility
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for(ElementOfRightTable element : utilityTable.elements){
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                }
            }

            // If we should try to expand the right side of this rule

            if(shouldExpandRightSide){
                expandTimes++;
                expandSecondRight(antecedentSupport, utilityTable, antecedent, newConsequent, antecedentLength, consequentLength + 1);
            }
        }

        // Check the maximum memory usage
        MemoryLogger.getInstance().checkMemory();
    }

    private void expandSecondRight(double antecedentSupport, rightUtilityTable table, List<Integer> antecedent,
                                   List<Integer> consequent, int antecedentLength, int consequentLength) throws IOException {
        if (DEBUG) {
            System.out.print(antecedent);
            System.out.print("-->");
            System.out.println(consequent);
        }

        // We first find the largest item on the right side of the rule
        int lastItemInConsequent = consequent.get(consequent.size() - 1);

        // We create a new map where we will build the utility table for the new rules that
        // will be created by adding an item to the current rule.
        // Key: an item appended to the rule     Value: the utility-table of the corresponding new rule
        Map<Integer, rightUtilityTable> mapItemsTables = new HashMap<>();


        Map<Integer, Double> RSUTable = new HashMap<>();
        Map<Integer, Double> RERSPEU = new HashMap<>();
        double[] UPS;
		// for each sequence containing the original rule (according to its utility table)
        for(ElementOfRightTable element : table.elements){
            if (RSUPruning || RSPEU) {
                table.totalUtilityRight -= element.utilityRight;
                table.totalUtility -= element.utility;
            }


            UPS = UPSL[element.numeroSequence];
            // Optimisation: if the "rutil" is 0 for that rule in that sequence,
            // we do not need to scan this sequence.
            // right utility = 0 means that sequence cannot expand for larger rule of consequent.
            if (element.utilityRight == 0)
                continue;

            // Get the sequence
            SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);
            HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);
            int idx3 = lastIndexOfSeq.get(element.numeroSequence);

            //============================================================
            // case 1 and case 2
            // Case 1: for each itemset in BETA or AFTER BETA.....

            // For each itemset after gama:
            for(int i = element.positionGamaItemset; i < sequence.size(); i++){
                // get the itemset
                List<Integer> itemsetI = sequence.getItemsets().get(i);

                // For each item
                for(int j = 0; j < itemsetI.size(); j++){
                    Integer itemJ = itemsetI.get(j);
                    // Check if the item is greater than items in the consequent of the rule
                    // according to the lexicographical order
                    if(i == element.positionGamaItemset && itemJ <= lastItemInConsequent){
                        // if not, then we continue because that item cannot be added to the rule
                        continue;
                    }

                    // ======= Otherwise, we need to update the utility table of the item ====================

                    int idx2 = idxs.get(itemJ % (maxItem + 1));
                    // Get the utility table of the item
                    if (i == element.positionGamaItemset) {
                        itemJ += maxItem + 1;
                    }
                    if (RSUPruning) {
                        if (RSUTable.get(itemJ) == null) {
                            RSUTable.put(itemJ, element.utilityRight + element.utility);
                        } else {
                            RSUTable.put(itemJ, element.utilityRight + element.utility + RSUTable.get(itemJ));
                        }
                    }

                    if (RSPEU) {
                        double utility;
                        if (idx2 + 1 == idx3)
                            utility = element.utilityRight;
                        else
                            utility = UPS[idx3] - UPS[idx2 - 1];
                        if (RERSPEU.get(itemJ) == null) {
                            RERSPEU.put(itemJ, utility + element.utility);
                        } else {
                            RERSPEU.put(itemJ, utility + element.utility + RERSPEU.get(itemJ));
                        }
                    }

                    rightUtilityTable tableItemJ = mapItemsTables.get(itemJ);
                    if (RSUPruning) {
                        if (RSUTable.get(itemJ) + table.totalUtilityRight + table.totalUtility < minutil) {
                            if (tableItemJ != null) {
                                mapItemsTables.remove(itemJ);
                            }
                            continue;
                        }
                    }

                    if (RSPEU) {
                        if (RERSPEU.get(itemJ) + table.totalUtility + table.totalUtilityRight < minutil) {
                            if (tableItemJ != null) {
                                mapItemsTables.remove(itemJ);
                            }
                            if (DEBUG) {
                                System.out.println("\n\t  LOW UTILITY SEQ. RULE: " + antecedent +
                                        " --> " + consequent  + "," + itemJ % (maxItem + 1) + "   utility " + table.totalUtility
                                        + " rsu : " + RERSPEU.get(itemJ)
                                        + " RPEU : " + table.totalUtilityRight
                                );
                            }
                            continue;
                        }
                    }


                    if(tableItemJ == null){
                        tableItemJ = new rightUtilityTable();
                        mapItemsTables.put(itemJ, tableItemJ);
                    }

                    //==========

                    // We will add a new element (line) in the utility table

                    ElementOfRightTable newElement = new ElementOfRightTable(element.numeroSequence);

                    // We will update the utility by adding the utility of item J
                    double profitItemJ = sequence.getUtilities().get(i).get(j);
                    newElement.utility = element.utility + profitItemJ;

                    // we will copy the "rutil" value from the original rule
                    // but we will subtract the utility of the item J
                    // we will copy the position of Alpha and Beta in that sequences because it
                    // does not change
                    newElement.positionGamaItemset = i;

                    // Then, we will scan itemsets after the beta position in the sequence
                    // We will subtract the utility of items that are smaller than item J
                    // according to the lexicographical order from "rutil" because they
                    // cannot be added anymore to the new rule.

                    // for each such itemset


                    newElement.utilityRight = UPS[idx3] - UPS[idx2];


                    // Now that we have created the element for that sequence and that new rule
                    // , we will add the utility table of that new rule
                    tableItemJ.addElement(newElement);
                }
            }
        }


        itemTableCount += mapItemsTables.size();
        // For each new rule
        for(Map.Entry<Integer, rightUtilityTable> entryItemTable :  mapItemsTables.entrySet()){

            // We get the item and its utility table
            Integer item = entryItemTable.getKey();
            rightUtilityTable utilityTable = entryItemTable.getValue();


            // We check if we should try to expand its right side
            boolean shouldExpandRightSide;

            shouldExpandRightSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityRight() >= minutil
                    && consequentLength+1 < maxSizeConsequent;

            // check if the rule is high utility
            boolean isHighUtility = utilityTable.getTotalUtility() >= minutil;

            // We create the consequent for the new rule by appending the new item
            List<Integer> newConsequent = new ArrayList<>(consequent);
            int newSid = utilityTable.elements.get(0).numeroSequence;
            int newGama = utilityTable.elements.get(0).positionGamaItemset;
            for (ElementOfRightTable ele: table.elements){
                if (ele.numeroSequence == newSid) {
                    if (newGama == ele.positionGamaItemset) {
                        newConsequent.add(item % (maxItem + 1));
                    }else {
                        newConsequent.add(-1);
                        newConsequent.add(item % (maxItem + 1));
                    }
                    break;
                }
            }

            double confidence = utilityTable.getRuleSize() / (double) antecedentSupport;

            // If the rule is high utility and high confidence
            if(isHighUtility && confidence >= minConfidence){
                ruleCount++;
                if(DEBUG){
                    System.out.println("\n\t  HIGH UTILITY SEQ. RULE: " + antecedent +
                            " --> " + consequent  + "," + item + "   utility " + utilityTable.totalUtility
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for(ElementOfRightTable element : utilityTable.elements){
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                }
            } else {
                if (DEBUG) {
                    System.out.println("\n\t  LOW UTILITY RULE: " + antecedent +
                            " --> " + consequent + "," + item + "   utility " + utilityTable.totalUtility
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for(ElementOfRightTable element : utilityTable.elements){
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                }
            }

            // If we should try to expand the right side of this rule

            if(shouldExpandRightSide){
                expandTimes++;
                expandSecondRight(antecedentSupport, utilityTable, antecedent, newConsequent, antecedentLength, consequentLength + 1);
            }
        }
        // Check the maximum memory usage
        MemoryLogger.getInstance().checkMemory();
    }


    /**
     * Print statistics about the last algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============================================================================");
        System.out.println("--- Total-ordered HUSRM algorithm for high utility sequential rule mining ---");
        System.out.println("=============================================================================");
        System.out.println("\tminutil: " + minutil);
        System.out.println("\tSequential rules count: " + ruleCount);
        System.out.println("\tTotal time : " + (double)(timeEnd - timeStart) / 1000 + " s");
        System.out.println("\tMax memory (mb) : "
                + MemoryLogger.getInstance().getMaxMemory());
        System.out.println("\texpand times count: " + expandTimes);
        System.out.println("\tNumber of utility table: " + itemTableCount);
        System.out.println("=============================================================================");
    }

}

