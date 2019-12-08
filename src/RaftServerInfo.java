class RaftServerInfo {
  
    private int id;
    private String hostname; 
    private int port;

    public RaftServerInfo(int id, String hostname, int port) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
    }

    public int getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }
}
