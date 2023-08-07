import java.util.ArrayList;
import java.util.List;

public class UtilityTable {
    // the list of elements (lines) in that utility table
    List<ElementOfTable> elements = new ArrayList<ElementOfTable>();
    // the total utility in that table
    double totalUtility = 0;

    // the toal lutil values of elements in that table
    double totalUtilityLeft = 0;
    // the toal rutil values of elements in that table
    double totalUtilityRight = 0;

    /**
     * Constructor
     */
    public UtilityTable() {

    }

    /**
     * Add a new element (line) to that table
     *
     * @param element the new element
     */
    public void addElement(ElementOfTable element) {
        // add the element
        elements.add(element);
        // make the sum of the utility, lutil, rutil and lrutil values
        totalUtility += element.utility;
        totalUtilityLeft += element.utilityLeft;
        totalUtilityRight += element.utilityRight;
    }

    public double getTotalUtility() {
        return totalUtility;
    }

    public double getTotalUtilityLeft() {
        return totalUtilityLeft;
    }

    public double getTotalUtilityRight() {
        return totalUtilityRight;
    }

    public double getRuleSize() {
        return elements.size();
    }

}