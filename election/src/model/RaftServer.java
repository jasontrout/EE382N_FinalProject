package model;

import rmi.RaftRMI;
import rmi.RaftRMIInterface;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RaftServer implements RaftRMIInterface {

    private Host host;
    private long term;
    private String voteFor;// return true when voted for first election request
    private RaftHostState hostState = RaftHostState.FOLLOWER;//at first, everyone is follower
    private List<RaftMessage> raftLogs = new ArrayList<>();
    private String leader;

    private Timer electionTimeoutTimer;
    private long electionTimeout;//follower to candidate
    private List<Host> hostVoteMe;

    //leader heartbeat
    private long electionTimeoutBase = 150L;
    private Timer heartbeatTimer;
    private long heartbeatTimeout;
    private List<Host> peersInCluster;
    private int serverNumInCluster;
    private Executor executor = Executors.newCachedThreadPool(); //a pool of thread
    //private Executor executor = Executors.newFixedThreadPool(10);
    Random random = new Random();

    public String getRaftServerId(){
        return host.getId();
    }

    public long getTerm() {
        return term;
    }

    public void setTerm(long term) {
        this.term = term;
        voteFor = null;
    }

    public String getVoteFor() {
        return voteFor;
    }

    public void setVoteFor(String voteFor) {
        this.voteFor = voteFor;
    }

    public long getElectionTimeout() {
        return electionTimeout;
    }

    public void setElectionTimtout(long electionTimeout) {
        this.electionTimeout = electionTimeout;
    }

    public RaftHostState getHostState() {
        return hostState;
    }

    public void setHostState(RaftHostState hostState) {
        this.hostState = hostState;
    }

    public int getLogLastIndex(){
        return raftLogs.size();
    }

    public long getLogLastTerm(){
        if(raftLogs.size()>0)
            return raftLogs.get(raftLogs.size()-1).getTerm();
        return -1;//-1 means no log, all valid log start with 0
    }

    public RaftServer(){
        this.electionTimeout = electionTimeoutBase;
        this.heartbeatTimeout = electionTimeoutBase/2;//heartbeat should be less than electionTimeout, just take half of it
    }

    public void setServersInCluster(Host myself, List<Host> servers){
        host = myself;
        peersInCluster = servers;
        serverNumInCluster = servers.size()+1;
    }

    public void startElectionTimer(){
        long electionTime = electionTimeout + random.nextInt((int)electionTimeout+1);
        //setElectionTimeout(electionTime);
        electionTimeoutTimer = new Timer();
        //every time execute one task, and get a new random election time
        electionTimeoutTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                System.out.println("start election timer");
                startElection();
            }
        }, electionTime, electionTime);

    }

    public void populateFollowerConfig(long currentTerm, String leaderId){
        setTerm(currentTerm);
        setHostState(RaftHostState.FOLLOWER);

        if(electionTimeoutTimer!=null) {
            electionTimeoutTimer.cancel();
        }
        if(leader!=null && leader.equals(host.getId()) && heartbeatTimer!=null){
            heartbeatTimer.cancel();
        }
        startElectionTimer(); //todo: comment to test heartbeat
        leader = leaderId;
    }

    public void startElection(){
        synchronized (this){

            setHostState(RaftHostState.CANDIDATE);//I am candidate now
            setVoteFor(null);
            setTerm(getTerm()+1);
            //vote for myself
            hostVoteMe = new ArrayList<>();
            hostVoteMe.add(host);
        }
        //3 conditions: 1.receive majority votes become leader 2. receive appendEntry from leader 3. split votes
        for(Host h : peersInCluster){
            executor.execute(sendRequestVoteRunnable(h));
        }
    }

    public Runnable sendRequestVoteRunnable(Host h){
        return new Runnable() {
            @Override
            public void run() {
                RaftServer raftServer = RaftServer.this;
                long currentTerm;
                String candidateId;
                int logLastIndex;
                long logLastTerm;

                synchronized (raftServer){
                    currentTerm = getTerm();
                    candidateId = host.getId();
                    logLastIndex = getLogLastIndex();
                    logLastTerm = getLogLastTerm();
                    setVoteFor(candidateId);
                }

                try {
                    if(getHostState().equals(RaftHostState.LEADER)){
                        System.out.println("I am leader, exist send request vote, my id " + host.getId());
                        return;
                    }
                    System.out.println("send request vote message");
                    RaftMessage msg = RaftRMI.getRaftRMI().requestVote(h, currentTerm, logLastIndex, logLastTerm, candidateId);
                    System.out.println("we got message " + msg.isSuccessful());
                    synchronized (raftServer){
                        if(msg != null && msg.isSuccessful() && getHostState().equals(RaftHostState.CANDIDATE)){
                            hostVoteMe.add(h);
                            if(hostVoteMe.size() > serverNumInCluster/2){
                                System.out.println("we have a leader " + host.getId());
                                electionTimeoutTimer.cancel();
                                if(heartbeatTimer != null) {
                                    heartbeatTimer.cancel();
                                }
                                setHostState(RaftHostState.LEADER);
                                leader = host.getId();
                                //todo: start count index for replication
                                startHeartBeat();
                                //todo: send entry for replication
                            }
                        }else if(msg != null && !msg.isSuccessful()){
                            if(msg.getTerm() > currentTerm){//someone is leader
                                populateFollowerConfig(msg.getTerm(), null);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("RaftServer: sendRequestVoteRunnable exception "+e.getMessage()+ " "+e.getCause());
                }
            }
        };
    }

    public void startHeartBeat(){
        heartbeatTimer = new Timer();
        //every time execute one task, and get a new random election time
        heartbeatTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                for(Host h : peersInCluster){
                    executor.execute(heartbeatAppendEntryRunnable(h));
                }
            }
        }, 0, heartbeatTimeout);
    }

    public Runnable heartbeatAppendEntryRunnable(Host h){
        return new Runnable() {
            @Override
            public void run() {
                RaftServer raftServer = RaftServer.this;
                long currentTerm;
                int logLastIndex;
                long logLastTerm;
                String leader;
                synchronized (raftServer){
                    currentTerm = getTerm();
                    logLastIndex = getLogLastIndex();
                    logLastTerm = getLogLastTerm();
                    leader = host.getId();
                }
                if(!getHostState().equals(RaftHostState.LEADER)){
                    return;
                }
                RaftMessage msg = RaftRMI.getRaftRMI().appendEntry(h, currentTerm, logLastIndex, logLastTerm, leader, true);
                synchronized (raftServer){
                    if(msg != null){
                        if(!msg.isSuccessful()){
                            if(currentTerm < msg.getTerm()) {
                                populateFollowerConfig(msg.getTerm(), null);
                            }
                        }
                    }
                }
            }
        };
    }


    @Override
    public RaftMessage requestVote(long term, String candidateId, int logLastIndex, long logLastTerm) throws RemoteException {
        RaftMessage msg = null;
        long currentTerm;
        String currentVoted;
        int currentLogLastIndex;
        long currentLogLastTerm;
        synchronized (this){
            System.out.println("RaftServer: requestVote");
            currentTerm = getTerm();
            currentVoted = getVoteFor();
            currentLogLastIndex = getLogLastIndex();
            currentLogLastTerm = getLogLastTerm();

        }

        boolean voteIt = validRequest(currentTerm, currentVoted, currentLogLastIndex, currentLogLastTerm, term, candidateId, logLastIndex, logLastTerm);
        System.out.println("RaftServer: requestVote, voteIt "+voteIt);
        if(voteIt){
            synchronized (this){
                setVoteFor(candidateId);
                populateFollowerConfig(term, null);
            }
            msg = new RaftMessage(currentTerm, true, MessageType.REQUEST_VOTE);
        }else{
            msg = new RaftMessage(currentTerm, false, MessageType.REQUEST_VOTE);
        }
        System.out.println("RaftServer: requestVote, msg "+ msg.isSuccessful() + " term "+msg.getTerm() + " vote "+candidateId);
        return msg;
    }

    public boolean validRequest(long currentTerm, String currentVoted, int currentLogLastIndex, long currentLogLastTerm,
                                long term, String candidateId, int logLastIndex, long logLastTerm){
        if(candidateId != null && candidateId.length() > 0 && term > 0){
            if(term <  currentTerm){//I have higher term
                return false;
            }else if(term == currentTerm){
                if(currentVoted.equals(candidateId)){
                    if(currentLogLastTerm > logLastTerm || (currentLogLastTerm == logLastTerm && currentLogLastIndex > logLastIndex)){
                        return false;
                    }
                }else {//the host already vote
                    return false;
                }
            }else{//even term>currentTerm, the log size cloud be smaller
                return (!(currentLogLastTerm > logLastTerm || (currentLogLastTerm == logLastTerm && currentLogLastIndex > logLastIndex)));
            }
        }
        return false;
    }

    @Override
    public RaftMessage appendEntry(long term, String leaderId, int logCurrentIndex, long logCurrentTerm, boolean isHeartBeats) throws RemoteException {
        RaftMessage msg = null;
        long currentTerm;
        synchronized (this){
            currentTerm = getTerm();

        }
        if(term < currentTerm){
            msg = new RaftMessage(currentTerm, false, MessageType.APPEND_ENTRY);
        } else if(isHeartBeats){
            synchronized (this){
                populateFollowerConfig(currentTerm, leaderId);
                //todo: ??update index
            }
            msg = new RaftMessage(currentTerm, true, MessageType.APPEND_ENTRY);
        }
        System.out.println("RaftServer: appendEntry heartbeat, msg "+msg.isSuccessful()+" term  "+msg.getTerm());
        return msg;
    }
}
