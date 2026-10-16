/**
 * Purpose: Holds configurable Presence settings.
 * Why it is used: It keeps values such as the camera number and recognition threshold in one clear place.
 */
package com.presence.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "presence")
public class PresenceProperties {
    private String dataDirectory = "data";
    private int cameraIndex = 0;
    private double recognitionThreshold = 95.0;
    private int frameWidth = 640;
    private int frameHeight = 480;

    public String getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(String dataDirectory) { this.dataDirectory = dataDirectory; }
    public int getCameraIndex() { return cameraIndex; }
    public void setCameraIndex(int cameraIndex) { this.cameraIndex = cameraIndex; }
    public double getRecognitionThreshold() { return recognitionThreshold; }
    public void setRecognitionThreshold(double recognitionThreshold) { this.recognitionThreshold = recognitionThreshold; }
    public int getFrameWidth() { return frameWidth; }
    public void setFrameWidth(int frameWidth) { this.frameWidth = frameWidth; }
    public int getFrameHeight() { return frameHeight; }
    public void setFrameHeight(int frameHeight) { this.frameHeight = frameHeight; }
}
