class RaftServerInfo {
  
    private Long id;
    private String hostname; 
    private Integer port;

    public RaftServerInfo(Long id, String hostname, Integer port) {
        this.id = id;
        this.hostname = hostname;
        this.port = port;
    }

    public Long getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }

    public Integer getPort() {
        return port;
    }
}
