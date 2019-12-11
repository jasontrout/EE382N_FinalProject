import java.io.Serializable;

public class RaftEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private long index;
    private long term;
    private String command;
  
    public RaftEntry(long index, long term, String command) { 
        this.index = index;
        this.term = term;
        this.command = command;
    }

    public long getIndex() {
        return index;
    }
 
    public long getTerm() {
        return term;
    }

    public String getCommand() {
        return command;
    }

}
