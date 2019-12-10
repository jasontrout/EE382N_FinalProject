class RaftServerInfo {
  
    private Long id;
    private String hostname; 

    public RaftServerInfo(Long id, String hostname) {
        this.id = id;
        this.hostname = hostname;
    }

    public Long getId() {
        return id;
    }

    public String getHostname() {
        return hostname;
    }
}
