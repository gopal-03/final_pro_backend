package com.example.demo.dtos;

public class RegisterRequest {
    private String username;
    private String faceImage; // Base64-encoded image string

    // Getters and setters
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getFaceImage() {
        return faceImage;
    }
    public void setFaceImage(String faceImage) {
        this.faceImage = faceImage;
    }
}
