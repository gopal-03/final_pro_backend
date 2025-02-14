package com.example.demo.dtos;

public class IdentifyResponse {
    private String username;

    public IdentifyResponse(String username) {
        this.username = username;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
}
