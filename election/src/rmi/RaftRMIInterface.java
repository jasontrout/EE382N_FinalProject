package rmi;


import model.RaftMessage;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RaftRMIInterface extends Remote {

    //candidate invoke to get vote
    public RaftMessage requestVote(long term, String candidateId, int logLastIndex, long logLastTerm) throws RemoteException;

    public RaftMessage appendEntry(long term, String leaderId, int logCurrentIndex, long logCurrentTerm, boolean isHeartBeats) throws RemoteException;

}
