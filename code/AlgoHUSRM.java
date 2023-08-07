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


public class AlgoHUSRM {
    /**
     * start time of latest execution
     */
    long timeStart = 0;
    /**
     * end time of latest execution
     */
    long timeEnd = 0;
    /**
     * number of rules generated
     */
    int ruleCount;

    /** number of utility table
     * it is equivalent to the candidate rule's number in the paper of TotalSR
     * */
    long itemTableCount = 0;

    private long expandTimes = 0;

    /**
     * minimum confidence
     **/
    double minConfidence;

    /**
     * minimum support
     */
    double minutil;

    int maxItem = -1;
    /**
     * this is the sequence database
     */
    SequenceDatabaseWithUtility database;

    /**
     * this buffered writer is used to write the output file
     */
    BufferedWriter writer = null;

    /**
     * this is a map where the KEY is an item and the VALUE is the list of sequences
     * /* containing the item.
     */
    private Map<Integer, ListSequenceIDs> mapItemSequences;

    /**
     * this is a contrainst on the maximum number of item that the left side of a rule should
     * /* contain
     */
    private int maxSizeAntecedent;

    /**
     * this is a contrainst on the maximum number of item that the right side of a rule should
     * /* contain
     */
    private int maxSizeConsequent;

    private int maximumRemoveTimes = Integer.MAX_VALUE;

    private double[][] UPSL;
    private List<HashMap<Integer, Integer>> indicex = new ArrayList<>();
    private List<HashMap<Integer, Integer>> itemToSeqPos = new ArrayList<>();
    private HashMap<Integer, Integer> lastIndexOfSeq = new HashMap<>();   // key: seq_number, value: index of the last item in this sequence.

    /**
     * this variable is used to activate the debug mode.  When this mode is activated
     *  some additional information about the algorithm will be shown in the console for
     *  debugging
     **/
    final boolean DEBUG = false;

    ////// ================ STRATEGIES ===============================
    // Various strategies have been used to improve the performance of HUSRM.
    // The following boolean values are used to deactivate these strategies.

    /**
     * Strategy 1: remove items with a sequence estimated utility < minutil
     */
    private boolean deactivateStrategy1 = false;

    private boolean confidencePruning = true;

    private boolean RSPEU = true;

    /**
     * Default constructor
     */
    public AlgoHUSRM() {
    }


    /**
     * The main method to run the algorithm
     *
     * @param input                    an input file
     * @param output                   an output file
     * @param minConfidence            the minimum confidence threshold
     * @param minutil                  the minimum utility threshold
     * @param maxConsequentSize        a constraint on the maximum number of items that the right side of a rule should contain
     * @param maxAntecedentSize        a constraint on the maximum number of items that the left side of a rule should contain
     * @param maximumNumberOfSequences the maximum number of sequences to be used
     * @throws IOException if error reading/writing files
     */
    public void runAlgorithm(String input, String output,
                             double minConfidence, double minutil, int maxAntecedentSize, int maxConsequentSize,
                             int maximumNumberOfSequences) throws IOException {

        // save the minimum confidence parameter
        this.minConfidence = minConfidence;

        // save the constraints on the maximum size of left/right side of the rules
        this.maxSizeAntecedent = maxAntecedentSize;
        this.maxSizeConsequent = maxConsequentSize;

        // reinitialize the number of rules found
        ruleCount = 0;
        this.minutil = minutil;

        // We reset the tool for calculating the maximum memory usage
        MemoryLogger.getInstance().reset();

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

        MemoryLogger.getInstance().checkMemory();


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

        // FIRST STEP: We will calculate the estimated profit of each single item

        // if this strategy has not been deactivated
        if (deactivateStrategy1 == false) {

            maxItem = -1;
            // This map will store pairs of (key: item   value: estimated profit of the item)
            Map<Integer, Double> mapItemEstimatedUtility = new HashMap<Integer, Double>();

            // We read the database.
            // For each sequence
            for (SequenceWithUtility sequence : database.getSequences()) {

                // for each itemset in that sequence
                for (List<Integer> itemset : sequence.getItemsets()) {

                    // for each item
                    for (Integer item : itemset) {
                        // get the current sequence estimated utility of that item
                        Double estimatedUtility = mapItemEstimatedUtility.get(item);
                        maxItem = Math.max(maxItem, item);
                        // if we did not see that item yet
                        if (estimatedUtility == null) {
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

            while (removeTimes < maximumRemoveTimes) {
                // If not item can be removed
                if (removeCount == 0) {
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
                            Integer item =  iteratorItem.next();
                            // get its utility value
                            Double utility = iteratorItemUtility.next();

                            // if the item is unpromising
                            if (mapItemEstimatedUtility.get(item) == null) {

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
                        if (itemset.isEmpty()) {
                            iteratorItemset.remove();
                            iteratorItemsetUtilities.remove();
                        }
                    }

                    // If the sequence has become empty, we remove the sequences from the database
                    if (sequence.size() == 0 || sequence.size() == 1) {
                        iteratorSequence.remove();
                    } else {
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
                                if (mapItemEstimatedUtility.get(item) - removeUtility < minutil) {
                                    removeCount++;
                                    mapItemEstimatedUtility.remove(item);
                                } else {
                                    mapItemEstimatedUtility.put(item, mapItemEstimatedUtility.get(item) - removeUtility);
                                }
                            }
                        }
                    }
                }
            }
        }



        // We create a map to store for each item, the list of sequences containing the item
        // Key: an item   Value:  the list of sequences containing the item
        mapItemSequences = new HashMap<Integer, ListSequenceIDs>();

        UPSL = new double[database.getSequences().size()][database.getLargestLength() + 1];

        // For each sequence
        for (int i = 0; i < database.getSequences().size(); i++) {
            // get i-th sequence
            SequenceWithUtility sequence = database.getSequences().get(i);
            HashMap<Integer, Integer> ItemPosition = new HashMap<>();
            HashMap<Integer, Integer> itemSeqToPos = new HashMap<>();
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
                    UPSL[i][index + 1] = UPSL[i][index] + sequence.getUtilities().get(j).get(itemSetIndex++);
                    ItemPosition.put(item, ++index);
                    itemSeqToPos.put(item, j);
                    // If the list does not exist, we will create it
                    if (numerosSequenceItem == null) {
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
            indicex.add(ItemPosition);
            itemToSeqPos.add(itemSeqToPos);
        }


        if (DEBUG) {
            for (int i = 0; i < UPSL.length; i++)
                System.out.println(Arrays.toString(UPSL[i]));
        }

        Set<Integer> items = mapItemSequences.keySet();

        // For each X (item)
        for (Integer item: items) {
            // antecedent --> item
            ListSequenceIDs sequenceIDsX = mapItemSequences.get(item);
            List<Integer> sequencesIDsX = sequenceIDsX.getSequences();

            HashMap<Integer, List<Integer>> Ys = getConsequentItem(item, sequencesIDsX);


            // For each Y
            for (Map.Entry<Integer, List<Integer>> entry: Ys.entrySet()) {
                int Y = entry.getKey();
                List<Integer> antecedent = new ArrayList<>();
                antecedent.add(item);
                double supportX = sequencesIDsX.size();

                Map<Integer, Integer> Positions = new HashMap<>(sequenceIDsX.getSeqAndItemsetPos());
                List<Integer> sequencesIDsXY = entry.getValue();
                // Get the support of the rule X ->Y
                double supportXY = sequencesIDsXY.size();

                // We create the utility table of rule X -> Y
                UtilityTable table = new UtilityTable();

                for (int sid: sequencesIDsXY) {
                    double[] prefixSum = UPSL[sid];
                    // Get the sequence
                    SequenceWithUtility sequence = database.getSequences().get(sid);
                    Positions.remove(sid);

                    // Create a new element in the table
                    ElementOfTable element = new ElementOfTable(sid);
                    element.utility = 0;

                    // we reset position alpha and beta
                    // calculate the utility of the rule, then insert the entry into utility table
                    int positionX = itemToSeqPos.get(sid).get(item);
                    int positionY = itemToSeqPos.get(sid).get(Y);
                    element.positionAlphaItemset = positionX;
                    element.positionBetaItemset = positionY;
                    element.positionGamaItemset = positionY;
                    int jX = Arrays.binarySearch(sequence.getItemsets().get(positionX).toArray(), item);
                    int jY = Arrays.binarySearch(sequence.getItemsets().get(positionY).toArray(), Y);
                    double utilityXPositionIJ = sequence.getUtilities().get(positionX).get(jX);
                    double profitYPositionIJ = sequence.getUtilities().get(positionY).get(jY);
                    element.utility = utilityXPositionIJ + profitYPositionIJ;
                    HashMap<Integer, Integer> idxS = indicex.get(sid);
                    int idx0 = 0, idx1 = 0, idx2 = 0;
                    idx0 = idxS.get(item);
                    idx1 = idxS.get(sequence.getItemsets().get(positionY).get(0));
                    idx2 = idxS.get(Y);

                    element.utilityLeft += prefixSum[idx1 - 1] - prefixSum[idx0];
                    element.utilityRight += prefixSum[lastIndexOfSeq.get(sid)] - prefixSum[idx2];

                    // Finally, we add the element of this sequence to the utility table of X->Y
                    table.addElement(element);
                }

                // We calculate the confidence of X -> Y
                double confidence = (supportXY / supportX);

                double conditionExpandLeft;
                double conditionExpandRight;

                conditionExpandLeft = table.getTotalUtilityLeft() + table.getTotalUtility() + table.getTotalUtilityRight();
                conditionExpandRight = table.getTotalUtility() + table.getTotalUtilityRight();

                // create the rule antecedent and consequence
                List<Integer> consequent = new ArrayList<>();
                consequent.add(Y);

                // if high utility with ENOUGH  confidence
                if ((table.getTotalUtility() >= minutil) && confidence >= minConfidence) {
                    ruleCount++;
                    // we output the rule
                }

                // if the left side size is less than the maximum size, we will try to expand the rule
                if (conditionExpandLeft >= minutil && maxAntecedentSize > 1) {
                    expandTimes++;
                    expandLeft(Positions, table, antecedent, consequent, 1, 1);
                }


                // if the right side size is less than the maximum size, we will try to expand the rule
                if (confidencePruning) {
                    if (confidence >= minConfidence) {
                        if (conditionExpandRight >= minutil && maxConsequentSize > 1) {
                            expandTimes++;
                            expandFirstRight(supportX, table, antecedent, consequent, 1, 1);
                        }
                    }
                } else {
                    if (conditionExpandRight >= minutil && maxConsequentSize > 1) {
                        expandTimes++;
                        expandFirstRight(supportX, table, antecedent, consequent, 1, 1);
                    }
                }
                //We will check the current memory usage
                MemoryLogger.getInstance().checkMemory();
            }
        }





        // save end time
        timeEnd = System.currentTimeMillis();

        saveRule(minutil, ruleCount, (double) (timeEnd - timeStart) / 1000, MemoryLogger.getInstance().getMaxMemory(), expandTimes, itemTableCount);

        // close the file
        writer.close();

        // after the algorithm ends, we don't need a reference to the database
        // anymore.
        database = null;
    }

    private HashMap<Integer, List<Integer>> getConsequentItem(int X, List<Integer> sequencesX) {
        HashMap<Integer, List<Integer>> ruleSids = new HashMap<>();
        for (int sid: sequencesX) {
            int pos = itemToSeqPos.get(sid).get(X);
            Map<Integer, Integer> seqPos = itemToSeqPos.get(sid);
            for (Map.Entry<Integer, Integer> entry: seqPos.entrySet()) {
                if (pos >= entry.getValue()) continue;
                List<Integer> sids = ruleSids.get(entry.getKey());
                if (sids == null) {
                    sids = new ArrayList<>();
                    ruleSids.put(entry.getKey(), sids);
                }
                sids.add(sid);
            }
        }
        return ruleSids;
    }


    /**
     * This method save a rule to the output file
     *
     * @param antecedent the left side of the rule
     * @param consequent the right side of the rule
     * @param utility    the rule utility
     * @param support    the rule support
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
    }


    private void saveRule(double minutil, long ruleCount, double runTime, double maxMemory, long candidateCount, long itemTableCount) throws IOException {


        // create a string buffer
        StringBuilder buffer = new StringBuilder();

        buffer.append("\tTotalSR");
        // write the left side of the rule (the antecedent)
        buffer.append("\n\tminutil: ").append(minutil);
        buffer.append("\n\tSequential rules count: ").append(ruleCount);
        buffer.append("\n\tTotal time : ").append(runTime).append(" s");
        buffer.append("\n\tMax memory (mb) : ").append(maxMemory);
        buffer.append("\n\texpand times count: ").append(candidateCount);
        buffer.append("\n\tutility table count: ").append(itemTableCount);

        writer.write(buffer.toString());
        writer.newLine();
    }

    private void expandLeft(Map<Integer, Integer> art, UtilityTable table, List<Integer> antecedent,
                            List<Integer> consequent, int antecedentLength, int consequentLength) throws IOException {


        // We first find the last items on the left side and right side of the rule
        int lastItemInAntecedent = antecedent.get(antecedent.size() - 1);
        int itemInConsequent = consequent.get(0);


        // get all extendable items that can be extended into antecedent
        Map<Integer, Double> leftPromisingItems = getLeftItemsAndUB(table, lastItemInAntecedent, itemInConsequent);


        // extend left with item
        for (Map.Entry<Integer, Double> entry : leftPromisingItems.entrySet()) {
            int itemJ = entry.getKey();
            double ub = entry.getValue();
            if (ub < minutil) continue;
            UtilityTable utilityTable = null;


            Map<Integer, Integer> ART = new HashMap<>();
            double[] UPS;


            // we create the new utility table
            for (ElementOfTable element : table.elements) {
                UPS = UPSL[element.numeroSequence];
                // Get the sequence
                SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);
                Set<Integer> itemsInThisSeq = sequence.getItems();
                if (!itemsInThisSeq.contains(itemJ % (maxItem + 1)))
                    continue;      // if itemJ does not exist in this seq we can continue;
                HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);
                HashMap<Integer, Integer> itemToPos = itemToSeqPos.get(element.numeroSequence);
                int idx3 = -1;
                if (element.positionBetaItemset != -1)
                    idx3 = idxs.get(sequence.getItemsets().get(element.positionBetaItemset).get(0));  // get the index of the first item in consequent in the sequence.

                int idx2 = idxs.get(itemJ % (maxItem + 1));
                if (idx2 < idxs.get(lastItemInAntecedent)) continue;
                // get the itemset's index that containing itemJ
                int position = itemToPos.get(itemJ % (maxItem + 1));


                if (utilityTable == null) {
                    utilityTable = new UtilityTable();
                }

                if (itemJ > (maxItem + 1) && position != element.positionAlphaItemset) continue;
                if (itemJ < (maxItem + 1) && position == element.positionAlphaItemset) continue;

                // create new entry of the utility table
                if (position < element.positionBetaItemset) {
                    ElementOfTable newElement = new ElementOfTable(element.numeroSequence);

                    // We will update the utility by adding the utility of item J
                    int j = Arrays.binarySearch(sequence.getItemsets().get(position).toArray(), itemJ % (maxItem + 1));

                    double profitItemJ = sequence.getUtilities().get(position).get(j);
                    newElement.utility = element.utility + profitItemJ; // rule's utility
                    newElement.utilityRight = element.utilityRight; // REPEU
                    newElement.positionBetaItemset = element.positionBetaItemset;   //
                    newElement.positionAlphaItemset = position;    // new Alpha
                    newElement.positionGamaItemset = element.positionGamaItemset;
                    newElement.utilityLeft = UPS[idx3 - 1] - UPS[idx2];     // new LEPEU
                    // end

                    // Now that we have created the element for that sequence and that new rule
                    // , we will add the utility table of that new rule
                    utilityTable.addElement(newElement);
                } else {
                    // update ART
                    ART.put(element.numeroSequence, position);
                }
            }


            if (utilityTable == null) continue;
            itemTableCount++;

            // We check if we should try to expand its left side
            boolean shouldExpandLeftSide;
            boolean shouldExpandRightSide;

            shouldExpandLeftSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityLeft() + utilityTable.getTotalUtilityRight() >= minutil
                    && antecedentLength + 1 < maxSizeAntecedent;
            shouldExpandRightSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityRight() >= minutil
                    && consequentLength + 1 < maxSizeConsequent;

            // check if the rule is high utility
            boolean isHighUtility = utilityTable.getTotalUtility() >= minutil;

            // We create the consequent for the new rule by appending the new item
            if (shouldExpandLeftSide || shouldExpandRightSide || isHighUtility) {
                int newItem = itemJ % (maxItem + 1);
                Map<Integer, Integer> newItemPosition = mapItemSequences.get(newItem).getSeqAndItemsetPos();
                if (itemJ > maxItem + 1) {
                    antecedent.add(itemJ % (maxItem + 1));
                    for (int seq : art.keySet()) {  // SID
                        // we still to maintain the old ART into new ART
                        // if the sid in the adding item's position and the old position equals to new adding item's position (I-extension)

                        if (newItemPosition.containsKey(seq) && Objects.equals(art.get(seq), newItemPosition.get(seq))) {     //Position.get(seq) == newItemPosition.get(seq)
                            ART.put(seq, newItemPosition.get(seq));
                        }
                    }
                } else {
                    antecedent.add(-1);
                    antecedent.add(itemJ % (maxItem + 1));
                    for (int seq : art.keySet()) {
                        if (newItemPosition.containsKey(seq) && art.get(seq) < newItemPosition.get(seq)) {     // S-extension
                            ART.put(seq, newItemPosition.get(seq));
                        }
                    }
                }
                // We calculate the confidence

                double confidence = utilityTable.getRuleSize() / (ART.size() + utilityTable.elements.size());

                // If the rule is high utility and high confidence
                if (isHighUtility && confidence >= minConfidence) {
                    ruleCount++;
                    if (DEBUG) {
                        System.out.println("\n\t  HIGH UTILITY SEQ. RULE: " + antecedent +
                                " --> " + consequent + "," + itemJ + "   utility " + utilityTable.totalUtility
                                + " support : " + utilityTable.elements.size()
                                + " confidence : " + confidence);

                        for (ElementOfTable element : utilityTable.elements) {
                            System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                    + " \t lutil: " + element.utilityLeft
                                    + " \t rutil: " + element.utilityRight
                                    + " alpha : " + element.positionAlphaItemset
                                    + " beta : " + element.positionBetaItemset);
                        }
                        System.out.println("==================");
                    }
                } else {
                    if (DEBUG) {
                        System.out.println("\n\t  LOW UTILITY RULE: " + antecedent +
                                " --> " + consequent + "," + itemJ + "   utility " + utilityTable.totalUtility
                                + " support : " + utilityTable.elements.size()
                                + " confidence : " + confidence);

                        for (ElementOfTable element : utilityTable.elements) {
                            System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                    + " \t lutil: " + element.utilityLeft
                                    + " \t rutil: " + element.utilityRight
                                    + " alpha : " + element.positionAlphaItemset
                                    + " beta : " + element.positionBetaItemset);
                        }
                        System.out.println("==================");
                    }
                }

                // If we should try to expand the left side of this rule
                if (shouldExpandLeftSide && utilityTable.getRuleSize() != 0) {
                    expandTimes++;
                    expandLeft(ART, utilityTable, antecedent, consequent, antecedentLength + 1, consequentLength);
                }


                if (confidencePruning) {
                    if (confidence >= minConfidence) {
                        if (shouldExpandRightSide) {
                            expandTimes++;

                            expandFirstRight((ART.size() + utilityTable.elements.size()),
                                    utilityTable, antecedent, consequent, antecedentLength + 1, consequentLength);
                        }
                    }
                } else {
                    if (shouldExpandRightSide) {
                        expandTimes++;

                        expandFirstRight((ART.size() + utilityTable.elements.size()),
                                utilityTable, antecedent, consequent, antecedentLength + 1, consequentLength);
                    }
                }

                antecedent.remove(antecedent.size() - 1);
                if (itemJ < maxItem + 1) antecedent.remove(antecedent.size() - 1);
            }
            MemoryLogger.getInstance().checkMemory();
        }
    }

    /**
     * This method is used to create new rule(s) by adding items to the right side of a rule
     *
     * @param table      the utility-table of the rule
     * @param antecedent the rule antecedent
     * @param consequent the rule consequent
     * @throws IOException if an error occurs while writing to file
     */
    private void expandFirstRight(double antecedentSupport, UtilityTable table, List<Integer> antecedent,
                                  List<Integer> consequent, int antecedentLength, int consequentLength) throws IOException {

        // We first find the last item on the right side of the rule
        int lastItemInConsequent = consequent.get(consequent.size() - 1);

        Map<Integer, Double> rightPromisingItems = getRightItemAndUB(table, lastItemInConsequent);

        for (Map.Entry<Integer, Double> entry : rightPromisingItems.entrySet()) {
            int itemJ = entry.getKey();
            double ub = entry.getValue();
            if (ub < minutil) continue;
            rightUtilityTable utilityTable = new rightUtilityTable();

            double[] UPS;

            for (ElementOfTable element : table.elements) {
                UPS = UPSL[element.numeroSequence];

                if (element.utilityRight == 0)
                    continue;

                SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);
                Set<Integer> itemsInThisSeq = sequence.getItems();

                if (!itemsInThisSeq.contains(itemJ % (maxItem + 1)))
                    continue;      // if itemJ does not exist in this seq we can continue;

                HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);
                int idx3 = lastIndexOfSeq.get(element.numeroSequence);

                int idx2 = idxs.get(itemJ % (maxItem + 1));

                if (idx2 < idxs.get(lastItemInConsequent)) continue;


                HashMap<Integer, Integer> itemToPos = itemToSeqPos.get(element.numeroSequence);
                // get the itemset's index that containing itemJ
                int position = itemToPos.get(itemJ % (maxItem + 1));


                if (itemJ > (maxItem + 1) && position != element.positionGamaItemset) continue;
                if (itemJ < (maxItem + 1) && position == element.positionGamaItemset) continue;
                // We will add a new element (line) in the utility table
                ElementOfRightTable newElement = new ElementOfRightTable(element.numeroSequence);

                // We will update the utility by adding the utility of item J
                int j = Arrays.binarySearch(sequence.getItemsets().get(position).toArray(), itemJ % (maxItem + 1));

                double profitItemJ = sequence.getUtilities().get(position).get(j);
                newElement.utility = element.utility + profitItemJ;

                newElement.positionGamaItemset = position;

                newElement.utilityRight = UPS[idx3] - UPS[idx2];


                // Now that we have created the element for that sequence and that new rule
                // , we will add the utility table of that new rule
                utilityTable.addElement(newElement);
            }


            itemTableCount++;
            // We get the item and its utility table

            // We check if we should try to expand its right side
            boolean shouldExpandRightSide;

            shouldExpandRightSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityRight() >= minutil
                    && consequentLength + 1 < maxSizeConsequent;

            // check if the rule is high utility
            boolean isHighUtility = utilityTable.getTotalUtility() >= minutil;

            // We create the consequent for the new rule by appending the new item
            if (itemJ > maxItem + 1) consequent.add(itemJ % (maxItem + 1));
            else {
                consequent.add(-1);
                consequent.add(itemJ);
            }
            double confidence = utilityTable.getRuleSize() / (double) antecedentSupport;

            // If the rule is high utility and high confidence
            if (isHighUtility && confidence >= minConfidence) {
                ruleCount++;
                if (DEBUG) {
                    System.out.println("\n\t  HIGH UTILITY SEQ. RULE: " + antecedent +
                            " --> " + consequent + "," + itemJ + "   utility " + utilityTable.totalUtility
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for (ElementOfRightTable element : utilityTable.elements) {
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                    System.out.println("==================");
                }
            } else {
                if (DEBUG) {
                    System.out.println("\n\t  LOW UTILITY RULE: " + antecedent +
                            " --> " + consequent + "," + itemJ + "   utility " + utilityTable.totalUtility
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for (ElementOfRightTable element : utilityTable.elements) {
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                    System.out.println("==================");
                }
            }

            // If we should try to expand the right side of this rule
            if (confidencePruning) {
                if (confidence >= minConfidence) {
                    if (shouldExpandRightSide) {
                        expandTimes++;
                        expandSecondRight(antecedentSupport, utilityTable, antecedent, consequent, antecedentLength, consequentLength + 1);
                    }
                }
            } else {
                if (shouldExpandRightSide) {
                    expandTimes++;
                    expandSecondRight(antecedentSupport, utilityTable, antecedent, consequent, antecedentLength, consequentLength + 1);
                }
            }

            consequent.remove(consequent.size() - 1);
            if (itemJ < maxItem + 1) consequent.remove(consequent.size() - 1);

            // Check the maximum memory usage
            MemoryLogger.getInstance().checkMemory();
        }

    }

    private void expandSecondRight(double antecedentSupport, rightUtilityTable table, List<Integer> antecedent,
                                   List<Integer> consequent, int antecedentLength, int consequentLength) throws IOException {
        // We first find the last item on the right side of the rule
        int lastItemInConsequent = consequent.get(consequent.size() - 1);

        Map<Integer, Double> rightPromisingItems = getRightItemAndUB(table, lastItemInConsequent);

        for (Map.Entry<Integer, Double> entry : rightPromisingItems.entrySet()) {
            int itemJ = entry.getKey();
            double ub = entry.getValue();

            if (ub < minutil) continue;

            rightUtilityTable utilityTable = new rightUtilityTable();

            double[] UPS;

            for (ElementOfRightTable element : table.elements) {
                UPS = UPSL[element.numeroSequence];

                if (element.utilityRight == 0)
                    continue;

                // Get the sequence
                SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);

                Set<Integer> itemsInThisSeq = sequence.getItems();

                if (!itemsInThisSeq.contains(itemJ % (maxItem + 1)))
                    continue;      // if itemJ does not exist in this seq we can continue;
                HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);
                int idx3 = lastIndexOfSeq.get(element.numeroSequence);

                int idx2 = idxs.get(itemJ % (maxItem + 1));
                if (idx2 < idxs.get(lastItemInConsequent)) continue;


                HashMap<Integer, Integer> itemToPos = itemToSeqPos.get(element.numeroSequence);
                // get the itemset's index that containing itemJ
                int position = itemToPos.get(itemJ % (maxItem + 1));

                // We will add a new element (line) in the utility table

                if (itemJ > maxItem + 1 && position != element.positionGamaItemset) continue;
                if (itemJ < maxItem + 1 && position == element.positionGamaItemset) continue;

                ElementOfRightTable newElement = new ElementOfRightTable(element.numeroSequence);

                int j = Arrays.binarySearch(sequence.getItemsets().get(position).toArray(), itemJ % (maxItem + 1));
                // We will update the utility by adding the utility of item J
                double profitItemJ = sequence.getUtilities().get(position).get(j);
                newElement.utility = element.utility + profitItemJ;

                newElement.positionGamaItemset = position;


                newElement.utilityRight = UPS[idx3] - UPS[idx2];


                // Now that we have created the element for that sequence and that new rule
                // , we will add the utility table of that new rule
                utilityTable.addElement(newElement);
            }

            itemTableCount++;
            // We check if we should try to expand its right side
            boolean shouldExpandRightSide;

            shouldExpandRightSide = utilityTable.getTotalUtility() + utilityTable.getTotalUtilityRight() >= minutil
                    && consequentLength + 1 < maxSizeConsequent;

            // check if the rule is high utility
            boolean isHighUtility = utilityTable.getTotalUtility() >= minutil;


            if (itemJ > maxItem + 1) consequent.add(itemJ % (maxItem + 1));
            else {
                consequent.add(-1);
                consequent.add(itemJ);
            }

            double confidence = utilityTable.getRuleSize() / (double) antecedentSupport;

            // If the rule is high utility and high confidence
            if (isHighUtility && confidence >= minConfidence) {
                ruleCount++;
                if (DEBUG) {
                    System.out.println("\n\t  HIGH UTILITY SEQ. RULE: " + antecedent +
                            " --> " + consequent + "," + itemJ + "   utility " + utilityTable.getTotalUtility()
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for (ElementOfRightTable element : utilityTable.elements) {
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                    System.out.println("==================");
                }
            } else {
                if (DEBUG) {
                    System.out.println("\n\t  LOW UTILITY RULE: " + antecedent +
                            " --> " + consequent + "," + itemJ + "   utility " + utilityTable.getTotalUtility()
                            + " support : " + utilityTable.elements.size()
                            + " confidence : " + confidence);

                    for (ElementOfRightTable element : utilityTable.elements) {
                        System.out.println("\t      SEQ:" + element.numeroSequence + " \t utility: " + element.utility
                                + " \t rutil: " + element.utilityRight);
                    }
                    System.out.println("==================");
                }
            }

            // If we should try to expand the right side of this rule
            if (confidencePruning) {
                if (confidence >= minConfidence) {
                    if (shouldExpandRightSide) {
                        expandTimes++;
                        expandSecondRight(antecedentSupport, utilityTable, antecedent, consequent, antecedentLength, consequentLength + 1);
                    }
                }
            } else {
                if (shouldExpandRightSide) {
                    expandTimes++;
                    expandSecondRight(antecedentSupport, utilityTable, antecedent, consequent, antecedentLength, consequentLength + 1);
                }
            }
            consequent.remove(consequent.size() - 1);
            if (itemJ < maxItem + 1) consequent.remove(consequent.size() - 1);
            // Check the maximum memory usage
            MemoryLogger.getInstance().checkMemory();
        }
    }

    private Map<Integer, Double> getLeftItemsAndUB(UtilityTable table, int lastItemInAntecedent, int itemInConsequent) {
        Map<Integer, Double> LERSPEUTable;

        LERSPEUTable = new HashMap<>();

        double[] UPS;

        for (ElementOfTable element: table.elements) {

            UPS = UPSL[element.numeroSequence];

            // Get the sequence
            SequenceWithUtility sequence = database.getSequences().get(element.numeroSequence);
            HashMap<Integer, Integer> idxs = indicex.get(element.numeroSequence);


            int idx3 = -1;
            if (element.positionBetaItemset != -1)
                idx3 = idxs.get(sequence.getItemsets().get(element.positionBetaItemset).get(0));  // get the index of the first item in consequent in the sequence.


            for (int i = element.positionAlphaItemset; i < element.positionBetaItemset; i++) {
                // get the itemset
                List<Integer> itemsetI = sequence.getItemsets().get(i);

                for (int j = 0; j < itemsetI.size(); j++) {
                    int itemJ = itemsetI.get(j);

                    // Check if the item is greater than items in the antecedent of the rule
                    // according to the lexicographical order
                    if (i == element.positionAlphaItemset && itemJ <= lastItemInAntecedent) {
                        // if not, then we continue because that item cannot be added to the rule
                        continue;
                    }


                    if (itemJ == itemInConsequent) continue;

                    int idx2 = idxs.get(itemJ % (maxItem + 1));
                    // Get the utility table of the item
                    if (i == element.positionAlphaItemset) {
                        itemJ += maxItem + 1;
                    }

                    if (RSPEU) {
                        if (i < element.positionBetaItemset) {
                            double utility;
                            utility = (UPS[idx3 - 1] - UPS[idx2 - 1]);
                            if (LERSPEUTable.get(itemJ) == null) {
                                LERSPEUTable.put(itemJ, utility + element.utility + element.utilityRight);
                            } else {
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
                }
            }
        }

        return LERSPEUTable;
    }


    private Map<Integer, Double> getRightItemAndUB(rightUtilityTable table, int lastItemInConsequent) {
        Map<Integer, Double> RERSPEU = null;

        if (RSPEU) RERSPEU = new HashMap<>();

        double[] UPS;

        for (ElementOfRightTable element : table.elements) {
            if (RSPEU) {
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

            int lenSeq = sequence.size();

            for (int i = element.positionGamaItemset; i < lenSeq; i++) {
                // get the itemset
                List<Integer> itemsetI = sequence.getItemsets().get(i);
                int lenItems = itemsetI.size();

                for (int j = 0; j < lenItems; j++) {
                    Integer itemJ = itemsetI.get(j);
                    // Check if the item is greater than items in the consequent of the rule
                    // according to the lexicographical order
                    if (i == element.positionGamaItemset && itemJ <= lastItemInConsequent) {
                        // if not, then we continue because that item cannot be added to the rule
                        continue;
                    }

                    int idx2 = idxs.get(itemJ % (maxItem + 1));
                    // Get the utility table of the item
                    if (i == element.positionGamaItemset) {
                        itemJ += maxItem + 1;
                    }

                    if (RSPEU) {
                        double utility;
                        utility = UPS[idx3] - UPS[idx2 - 1];

                        if (RERSPEU.get(itemJ) == null) {
                            RERSPEU.put(itemJ, utility + element.utility);
                        } else {
                            RERSPEU.put(itemJ, utility + element.utility + RERSPEU.get(itemJ));
                        }
                    }
                }
            }
        }


        return RERSPEU;
    }


    private Map<Integer, Double> getRightItemAndUB(UtilityTable table, int lastItemInConsequent) {
        Map<Integer, Double> RERSPEU = null;

        if (RSPEU) RERSPEU = new HashMap<>();
        double[] UPS;

        for (ElementOfTable element : table.elements) {
            if (RSPEU) {
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

            int lenSeq = sequence.size();

            for (int i = element.positionGamaItemset; i < lenSeq; i++) {
                // get the itemset
                List<Integer> itemsetI = sequence.getItemsets().get(i);
                int lenItems = itemsetI.size();
                // For each item
                for (int j = 0; j < lenItems; j++) {
                    Integer itemJ = itemsetI.get(j);
                    // Check if the item is greater than items in the consequent of the rule
                    // according to the lexicographical order
                    if (i == element.positionGamaItemset && itemJ <= lastItemInConsequent) {
                        // if not, then we continue because that item cannot be added to the rule
                        continue;
                    }

                    int idx2 = idxs.get(itemJ % (maxItem + 1));
                    // Get the utility table of the item
                    if (i == element.positionGamaItemset) {
                        itemJ += maxItem + 1;
                    }

                    if (RSPEU) {
                        double utility;
                        utility = UPS[idx3] - UPS[idx2 - 1];
                        if (RERSPEU.get(itemJ) == null) {
                            RERSPEU.put(itemJ, utility + element.utility);
                        } else {
                            RERSPEU.put(itemJ, utility + element.utility + RERSPEU.get(itemJ));
                        }
                    }
                }
            }
        }
        return RERSPEU;
    }


    /**
     * Print statistics about the last algorithm execution to System.out.
     */
    public void printStats() {
        System.out.println("=============================================================================");
        System.out.println("--- TotalSR algorithm ---");
        System.out.println("=============================================================================");
        System.out.println("\tminutil: " + minutil);
        System.out.println("\tSequential rules count: " + ruleCount);
        System.out.println("\tTotal time : " + (double) (timeEnd - timeStart) / 1000 + " s");
        System.out.println("\tMax memory (mb) : "
                + MemoryLogger.getInstance().getMaxMemory());
        System.out.println("\texpand times count: " + expandTimes);
        System.out.println("\tNumber of utility table: " + itemTableCount);
        System.out.println("=============================================================================");
    }
}


