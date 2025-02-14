package com.example.demo.controller;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.opencv.core.Mat;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.dtos.IdentifyRequest;
import com.example.demo.dtos.IdentifyResponse;
import com.example.demo.dtos.RegisterRequest;
import com.example.demo.model.UserFace;
import com.example.demo.repository.UserFaceRepository;
import com.example.demo.service.FaceService;
import java.util.Collections;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FaceController {

    @Autowired
    private UserFaceRepository userFaceRepository;

    @Autowired
    private FaceService faceService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest request) {
        try {
            BufferedImage image = decodeToImage(request.getFaceImage());

            Mat faceMat = faceService.detectAndAlignFace(image);
            if (faceMat == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                     .body("Face not detected or alignment failed.");
            }

            double[] features = faceService.extractFaceFeatures(faceMat);
            String featureString = Arrays.stream(features)
                                         .mapToObj(Double::toString)
                                         .collect(Collectors.joining(","));

            UserFace user = new UserFace();
            user.setUsername(request.getUsername());
            user.setFaceFeatures(featureString);
            userFaceRepository.save(user);

            return ResponseEntity.ok("User registered successfully");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error: " + e.getMessage());
        }
    }

    @PostMapping("/identify")
    public ResponseEntity<?> identifyUser(@RequestBody IdentifyRequest request) {
        try {
        	System.out.println("wifi strength"+getWifiSignalStrength());
        	System.out.println("wifi ip"+getWifiIpAddress());
            BufferedImage image = decodeToImage(request.getFaceImage());

            Mat faceMat = faceService.detectAndAlignFace(image);
            if (faceMat == null) {
                return ResponseEntity.ok(new IdentifyResponse("User not found"));
            }

            double[] queryFeatures = faceService.extractFaceFeatures(faceMat);

            List<UserFace> users = userFaceRepository.findAll();
            double minDistance = Double.MAX_VALUE;
            String identifiedUsername = null;

            for (UserFace user : users) {
                double[] storedFeatures = Arrays.stream(user.getFaceFeatures().split(","))
                                                .mapToDouble(Double::parseDouble)
                                                .toArray();
                double distance = faceService.computeEuclideanDistance(queryFeatures, storedFeatures);
                if (distance < minDistance) {
                    minDistance = distance;
                    identifiedUsername = user.getUsername();
                }
            }

            if (minDistance < 0.4) { // Adjust threshold based on testing
                return ResponseEntity.ok(new IdentifyResponse(identifiedUsername));
            } else {
                return ResponseEntity.ok(new IdentifyResponse("User not found"));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Error: " + e.getMessage());
        }
    }

    private BufferedImage decodeToImage(String base64Image) throws IOException {
        if (base64Image.contains(",")) {
            base64Image = base64Image.split(",")[1];
        }
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage image = ImageIO.read(bis);
        bis.close();
        return image;
    }
    
    
    //wifi Ip and Strength

    /**
     * Returns the IPv4 address associated with a Wi‑Fi network interface.
     * The method searches for common Wi‑Fi names like “wlan”, “Wi‑Fi”, or “wifi”.
     */
    private String getWifiIpAddress() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                // Check if the interface is up and not a loopback
                if (netint.isUp() && !netint.isLoopback()) {
                    String displayName = netint.getDisplayName().toLowerCase();
                    String name = netint.getName().toLowerCase();
                    // Look for common Wi‑Fi identifiers in the interface name
                    if (displayName.contains("wi-fi") || displayName.contains("wifi")
                            || name.contains("wlan") || name.contains("wifi")) {
                        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                            if (inetAddress instanceof Inet4Address) {
                                return inetAddress.getHostAddress();
                            }
                        }
                    }
                }
            }
        } catch (SocketException e) {
            // Log the exception (or handle it appropriately)
            e.printStackTrace();
        }
        return "Not found";
    }

    /**
     * Returns the Wi‑Fi signal strength as a string.
     * Uses different commands based on the operating system.
     */
    private String getWifiSignalStrength() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            // Windows: Use netsh command
            return getSignalStrengthWindows();
        } else if (os.contains("nix") || os.contains("nux")) {
            // Linux: Use nmcli command
            return getSignalStrengthLinux();
        } else if (os.contains("mac")) {
            // macOS: Use the airport command
            return getSignalStrengthMac();
        }
        return "Not Available";
    }

    private String getSignalStrengthWindows() {
        try {
            Process process = Runtime.getRuntime().exec("netsh wlan show interfaces");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Look for the "Signal" line (e.g., "Signal : 98%")
                if (line.startsWith("Signal")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        return parts[1].trim();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Not Available";
    }

    private String getSignalStrengthLinux() {
        try {
            Process process = Runtime.getRuntime().exec("nmcli -f ACTIVE,SSID,SIGNAL dev wifi");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Look for the active connection (line starting with "yes")
                if (line.startsWith("yes")) {
                    // Expected format: "yes  YourSSID  75"
                    String[] parts = line.split("\\s+");
                    if (parts.length >= 3) {
                        return parts[2] + "%";
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Not Available";
    }

    private String getSignalStrengthMac() {
        try {
            String[] command = {"/System/Library/PrivateFrameworks/Apple80211.framework/Versions/Current/Resources/airport", "-I"};
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // Look for the "agrCtlRSSI" line (e.g., "agrCtlRSSI: -55")
                if (line.startsWith("agrCtlRSSI:")) {
                    String[] parts = line.split(":");
                    if (parts.length >= 2) {
                        return parts[1].trim() + " dBm";
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "Not Available";
    }

}
