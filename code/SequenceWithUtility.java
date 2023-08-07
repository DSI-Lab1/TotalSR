
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class SequenceWithUtility {
    // this is the list of itemsets contained in this sequence
    // (each itemset is a list of Integers)
    private final List<List<Integer>> itemsets = new ArrayList<List<Integer>>();

    // this is the list of utility values corresponding to each item in the sequence
    private final List<List<Double>> profits = new ArrayList<List<Double>>();

    // this is a unique sequence id
    private int id;

    // this is the sequence utility (the sum of the utility of each item in that sequence)
    public double exactUtility;

    Set<Integer> items;

    public Set<Integer> getItems() {
        return items;
    }

    public void setItems(Set<Integer> items) {
        this.items = items;
    }

    /**
     * This method returns the list of utility values for all items in that sequence.
     * @return A list of list of doubles.  The first list represents the itemsets. Each itemset is a list
     * of Double where double values indicate the utility of each item.
     */
    public List<List<Double>> getUtilities() {
        return profits;
    }

    /**
     * Constructor. This method creates a sequence with a given id.
     * @param id the id of this sequence.
     */
    public SequenceWithUtility(int id) {
        this.id = id;
    }

    /**
     * Add an itemset to this sequence.
     * @param itemset An itemset (list of integers, where integers represent the items)
     */
    public void addItemset(List<Integer> itemset) {
        itemsets.add(itemset);
    }

    /**
     * Add the utility values of an itemset to this sequence
     * @param utilityValues a list of utility values corresponding to the item of an itemset.
     */
    public void addItemsetProfit(List<Double> utilityValues) {
        profits.add(utilityValues);
    }

    /**
     * Print this sequence to System.out.
     */
    public void print() {
        System.out.print(toString());
    }

    /**
     * Return a string representation of this sequence.
     */
    public String toString() {
        StringBuilder r = new StringBuilder("");
        // for each itemset
        for(int i = 0; i < itemsets.size(); i++){
            List<Integer> itemset = itemsets.get(i);
//		for (List<Integer> itemset : itemsets) {
            r.append('(');
            // for each item in the current itemset
            for (int j=0; j <itemset.size();j++)
            {
                int item = itemset.get(j);

                //for (Integer item : itemset) {
                r.append(item);
                r.append("[");
                r.append(profits.get(i).get(j));
                r.append("]");
                r.append(' ');
            }
            r.append(')');
        }
        // append the sequence utility of this sequence
        r.append("   sequenceUtility: " + exactUtility);

        return r.append("    ").toString();
    }

    /**
     * Get the sequence ID of this sequence.
     */
    public int getId() {
        return id;
    }

    /**
     * Get the list of itemsets in this sequence.
     * @return the list of itemsets. Each itemset is a list of Integers.
     */
    public List<List<Integer>> getItemsets() {
        return itemsets;
    }

    /**
     * Get the i-th itemset in this sequence.
     * @param index a positive integer i
     * @return the i-th itemset as a list of integers.
     */
    public List<Integer> get(int index) {
        return itemsets.get(index);
    }

    /**
     * Get the size of this sequence (number of itemsets).
     * @return the size (an integer).
     */
    public int size() {
        return itemsets.size();
    }

}

