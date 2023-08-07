import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ListSequenceIDsArrayList  implements ListSequenceIDs {
    // the internal array list representation
    List<Integer> list = new ArrayList<Integer>();

    // <key: sid, value: position>
    Map<Integer, Integer> seqAndItemsetPos = new HashMap<>();

    /**
     * Constructor
     */
    public ListSequenceIDsArrayList() {
    }

    /**
     * This method adds a sequence id to this list
     *
     * @param noSequence the sequence id
     */
    public void addSequenceID(int noSequence, int position) {
        list.add(noSequence);
        seqAndItemsetPos.put(noSequence, position);
    }


    /**
     * Get the number of sequence ids
     *
     * @return the number of sequence ids
     */
    public int getSize() {
        return list.size();
    }


    public List<Integer> getSequences() {
        return list;
    }


    public Map<Integer, Integer> getSeqAndItemsetPos() {
        return seqAndItemsetPos;
    }

    /**
     * Get a string representation of this list
     *
     * @return a string
     */
    public String toString() {
        return list.toString();
    }
}