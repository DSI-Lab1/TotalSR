/**
 * @Copyright (C), HITSZ
 * @Author Maohua Lyu, Wensheng, gan
 * @Date Created in 9:41 2022/10/9.
 * @Version 1.5
 * @Description
 */


import java.util.ArrayList;
import java.util.List;

public class rightUtilityTable {
    List<ElementOfRightTable> elements = new ArrayList<>();
    double totalUtility = 0;
    // the total rutil values of elements in that table
    double totalUtilityRight = 0;

    /**
     * Constructor
     */
    public rightUtilityTable() {

    }

    /**
     * Add a new element (line) to that table
     *
     * @param element the new element
     */
    public void addElement(ElementOfRightTable element) {
        // add the element
        elements.add(element);
        totalUtility += element.utility;
        totalUtilityRight += element.utilityRight;
    }

    public double getTotalUtility() {
        return totalUtility;
    }

    public double getTotalUtilityRight() {
        return totalUtilityRight;
    }

    public double getRuleSize() {
        return elements.size();
    }

}