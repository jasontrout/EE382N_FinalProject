
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {
    RaftRpcResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException;
    RaftRpcResult appendEntries(Long term, Long leaderId, Long prevLogIndex, RaftEntry[] entries, Long leaderCommit) throws RemoteException;
}
