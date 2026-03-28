package com.waddoc.domain.robot.service;

import org.springframework.stereotype.Component;

@Component
public class RobotStateCache {
    private volatile String lastMinimapJson = "{}";
    private volatile String lastStateJson   = "{}";
    private volatile String lastOdomJson    = "{}";
    private volatile String lastStatusJson  = "{}";

    public String getLastMinimapJson() { return lastMinimapJson; }
    public String getLastStateJson()   { return lastStateJson; }
    public String getLastOdomJson()    { return lastOdomJson; }
    public String getLastStatusJson()  { return lastStatusJson; }

    public void setLastMinimapJson(String json) { this.lastMinimapJson = json; }
    public void setLastStateJson(String json)   { this.lastStateJson   = json; }
    public void setLastOdomJson(String json)    { this.lastOdomJson    = json; }
    public void setLastStatusJson(String json)  { this.lastStatusJson  = json; }
}
