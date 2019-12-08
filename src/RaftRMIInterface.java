
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {
    RaftRpcResult requestVote(long term, long candidateId, long lastLogIndex, long lastLogTerm) throws RemoteException;
    RaftRpcResult appendEntries(long term, long leaderId, long prevLogIndex, RaftEntry[] entries, long leaderCommit) throws RemoteException;
}
