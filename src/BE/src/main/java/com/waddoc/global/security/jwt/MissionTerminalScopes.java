package com.waddoc.global.security.jwt;

public final class MissionTerminalScopes {

    public static final String IDENTITY_CHECK = "mission:identity-check";
    public static final String ISSUE_PATIENT_TOKEN = "session:issue-patient-token";
    public static final String VITALS_WRITE = "mission:vitals-write";

    private MissionTerminalScopes() {
    }
}
