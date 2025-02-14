package com.example.demo.dtos;


public class IdentifyRequest {
    private String faceImage; // Base64-encoded image string

    // Getter and setter
    public String getFaceImage() {
        return faceImage;
    }
    public void setFaceImage(String faceImage) {
        this.faceImage = faceImage;
    }
}

