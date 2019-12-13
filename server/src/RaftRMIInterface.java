import java.io.IOException;
import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {
    void addServerToCluster(Long id) throws RemoteException;
    RaftRequestVoteResult requestVote(Long term, Long candidateId, Long lastLogIndex, Long lastLogTerm) throws RemoteException, IOException;
    RaftAppendEntriesResult appendEntries(Long term, Long leaderId, Long prevLogIndex, Long prevLogTerm, RaftEntry[] entries, Long leaderCommit) throws RemoteException, IOException;
    RaftCmdResult processCmd(String cmd) throws RemoteException, IOException;
}
