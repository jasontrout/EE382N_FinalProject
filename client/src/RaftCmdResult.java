import java.io.Serializable;

public class RaftCmdResult implements Serializable {

    public static final long serialVersionUID = 1L;

    private boolean accepted;
    private Long leaderId;
    private String response;

    public RaftCmdResult() {
    }

    public RaftCmdResult(boolean accepted, Long leaderId, String response) {
        this.accepted = accepted;
        this.leaderId = leaderId;
        this.response = response;
    }

    public boolean getAccepted() {
      return accepted;
    }

    public Long getLeaderId() {
      return leaderId;
    }

    public String getResponse() {
      return response;
    }
}
