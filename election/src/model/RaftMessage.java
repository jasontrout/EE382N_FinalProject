package model;

import java.io.Serializable;

public class RaftMessage implements Serializable {

    private static final long serialVersionUID = 1L;
    private long term;
    private MessageType type;
    private long timestamp;
    private boolean isSuccessful; //return true already vote for a host when type is request vote
                                    //return true append entry success when type is append entry

    public RaftMessage(long term, boolean isSuccessful, MessageType type){
        this.term = term;
        this.isSuccessful = isSuccessful;
        this.type = type;
    }

    public boolean isSuccessful() {
        return isSuccessful;
    }

    public void setSuccessful(boolean successful) {
        isSuccessful = successful;
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
