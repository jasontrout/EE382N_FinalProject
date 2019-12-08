package rmi;


import model.RaftMessage;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {
    RaftMessage requestVote(long term, String candidateId, int logLastIndex, long logLastTerm) throws RemoteException;
    RaftMessage appendEntry(long term, String leaderId, int logCurrentIndex, long logCurrentTerm, boolean isHeartBeats) throws RemoteException;
    RaftMessage installSnapshot(long serverId, long term, long lastSnapshotIndex, long byteOffset, byte[] data, boolean isDone);
}
