import java.io.Serializable;

public class RaftAppendEntriesResult implements Serializable {

    public static final long serialVersionUID = 1L;

    private long term;
    private boolean success;

    public RaftAppendEntriesResult() {
    }
 
    public RaftAppendEntriesResult(long term, boolean success) { 
        this.term = term;
        this.success = success;
    }
  
    public long getTerm() {
        return term;
    }
  
    public boolean getSuccess() {
        return success;
    }
}
