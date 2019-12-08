import java.io.Serializable;

public class RaftRequestVoteResult implements Serializable {

    public static final long serialVersionUID = 1L;

    private long term;
    private boolean voteGranted;

    public RaftRequestVoteResult() {
    }
 
    public RaftRequestVoteResult(long term, boolean voteGranted) { 
        this.term = term;
        this.voteGranted = voteGranted;
    }
  
    public long getTerm() {
        return term;
    }
  
    public boolean getVoteGranted() {
        return voteGranted;
    }
}
