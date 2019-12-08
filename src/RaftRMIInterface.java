
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {
    RaftRequestVoteResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException;
    RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, RaftEntry[] entries, Long leaderCommit) throws RemoteException;
}
