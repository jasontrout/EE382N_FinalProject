
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {
    void requestVote(long term, long candidateId, long lastLogIndex, long lastLogTerm) throws RemoteException;
    void appendEntries(long term, long leaderId, long prevLogIndex, RaftEntry[] entries, long leaderCommit) throws RemoteException;
}
