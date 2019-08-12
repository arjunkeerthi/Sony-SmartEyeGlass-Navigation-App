package com.sony.smarteyeglass.extension.cameranavigation;

public class Detection {
    private String label;
    private double confidence;
    private double xCenter;
    private double yCenter;

    public Detection(String detectionStr) {
        int currIndex = 0;
        int dollarIndex = detectionStr.indexOf('$', currIndex);
        this.label = detectionStr.substring(currIndex, dollarIndex);
        currIndex += (dollarIndex - currIndex) + 1;
        dollarIndex = detectionStr.indexOf('$', currIndex);
        this.confidence = Double.parseDouble(detectionStr.substring(currIndex, dollarIndex));
        currIndex += (dollarIndex - currIndex) + 1;
        dollarIndex = detectionStr.indexOf('$', currIndex);
        this.xCenter = Double.parseDouble(detectionStr.substring(currIndex, dollarIndex));
        currIndex += (dollarIndex - currIndex) + 1;
        this.yCenter = Double.parseDouble(detectionStr.substring(currIndex));
    }

    public String getLabel() {
        return label;
    }

    public double getConfidence() {
        return confidence;
    }

    public double getxCenter() {
        return xCenter;
    }

    public double getyCenter() {
        return yCenter;
    }

    @Override
    public String toString() {
        return "Detection{" +
                "label='" + label + '\'' +
                ", confidence=" + confidence +
                ", xCenter=" + xCenter +
                ", yCenter=" + yCenter +
                '}';
    }
}
