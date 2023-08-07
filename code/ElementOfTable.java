
/**
 * @Copyright (C), HITSZ
 * @Author Maohua Lyu, Wensheng, gan
 * @Date Created in 9:41 2022/10/9.
 * @Version 1.5
 * @Description
 */

public class ElementOfTable {
    // the corresponding sequence id
    int numeroSequence;

    // the utility
    double utility;
    // the lutil value
    double utilityLeft;
    // the rutil value
    double utilityRight;
    // the alpha and beta values
    // record the first itemset's position of the antecedent
    int positionAlphaItemset = -1;

    //record the first itemset's position of the consequent
    int positionBetaItemset = -1;

    // record the last itemset's position of the consequent
    int positionGamaItemset = -1;


    /**
     * Constructor
     *
     * @param sequenceID the sequence id
     */
    public ElementOfTable(int sequenceID) {
        this.numeroSequence = sequenceID;
        this.utility = 0;
        this.utilityLeft = 0;
        this.utilityRight = 0;
    }
}