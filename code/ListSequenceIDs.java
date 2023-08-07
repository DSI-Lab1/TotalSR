import java.util.List;
import java.util.Map;

/**
 * This interface represents a list of sequences ids
 */

public interface ListSequenceIDs {

    /**
     * This method adds a sequence id to this list
     *
     * @param noSequence the sequence id
     */
    public abstract void addSequenceID(int noSequence, int position);


    /**
     * Get the number of sequence ids
     *
     * @return the number of sequence ids
     */
    public abstract int getSize();

    public abstract List<Integer> getSequences();

    public abstract Map<Integer, Integer> getSeqAndItemsetPos();
}
