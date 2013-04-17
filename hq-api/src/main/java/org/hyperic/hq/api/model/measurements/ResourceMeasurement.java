package org.hyperic.hq.api.model.measurements;

import java.io.Serializable;

public class ResourceMeasurement implements Serializable {
    private static final long serialVersionUID = -1470027516055434586L;
    private String resourceid;
    private String measurementalias;
    
    public String getResourceid() {
        return resourceid;
    }
    public void setResourceid(String resourceid) {
        this.resourceid = resourceid;
    }
    public String getMeasurementalias() {
        return measurementalias;
    }
    public void setMeasurementalias(String measurementalias) {
        this.measurementalias = measurementalias;
    }
}
