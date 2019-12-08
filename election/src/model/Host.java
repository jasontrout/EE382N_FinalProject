package model;

import java.io.Serializable;

public class Host implements Serializable {

    private static final long serialVersionUID = 1L;
    private String address;
    private int port;
    private String id;


    public Host(String address, int port, String id){
        this.address = address;
        this.port = port;
        this.id = id;
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString(){
        return "Host [address=" + address + ", port=" + port + ", id=" + id + "]";
    }
}
