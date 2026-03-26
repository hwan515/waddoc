package com.waddoc.domain.robot.service;

import org.springframework.stereotype.Component;

@Component
public class RobotStateCache {
    private volatile String lastMinimapJson = "{}";
    private volatile String lastStateJson   = "{}";
    private volatile String lastOdomJson    = "{}";

    public String getLastMinimapJson() { return lastMinimapJson; }
    public String getLastStateJson()   { return lastStateJson; }
    public String getLastOdomJson()    { return lastOdomJson; }

    public void setLastMinimapJson(String json) { this.lastMinimapJson = json; }
    public void setLastStateJson(String json)   { this.lastStateJson   = json; }
    public void setLastOdomJson(String json)    { this.lastOdomJson    = json; }
}
