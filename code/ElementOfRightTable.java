
/**
 * @Copyright (C), HITSZ
 * @Author Maohua Lyu, Wensheng, gan
 * @Date Created in 9:41 2022/10/9.
 * @Version 1.5
 * @Description
 */

public class ElementOfRightTable {
    int numeroSequence;

    // the utility
    double utility;
    // the rutil value
    double utilityRight;


    // record the last itemset's position of the consequent
    int positionGamaItemset = -1;


    /**
     * Constructor
     *
     * @param sequenceID the sequence id
     */
    public ElementOfRightTable(int sequenceID) {
        this.numeroSequence = sequenceID;
        this.utility = 0;
        this.utilityRight = 0;
    }
}
