package com.waddoc.domain.mission.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 운영 환경별 차량 단말 등록 정보를 설정 문자열에서 읽어 들인다.
 */
@Component
public class RobotTerminalRegistry {

    private final Map<String, TerminalRegistration> registrations;

    public RobotTerminalRegistry(
            @Value("${robot-terminal.registry:}") String registry
    ) {
        this.registrations = Map.copyOf(loadRegistrations(registry));
    }

    public boolean isConfigured() {
        return !registrations.isEmpty();
    }

    public Optional<TerminalRegistration> findByTerminalId(String terminalId) {
        return Optional.ofNullable(registrations.get(normalize(terminalId)));
    }

    private Map<String, TerminalRegistration> loadRegistrations(String registry) {
        LinkedHashMap<String, TerminalRegistration> loadedRegistrations = new LinkedHashMap<>();
        String normalizedRegistry = normalize(registry);
        if (normalizedRegistry == null) {
            return loadedRegistrations;
        }

        String[] rawEntries = normalizedRegistry.split(";");
        for (int i = 0; i < rawEntries.length; i++) {
            String rawEntry = normalize(rawEntries[i]);
            if (rawEntry == null) {
                continue;
            }
            TerminalRegistration registration = parseRegistration(rawEntry, i + 1);
            loadedRegistrations.put(registration.terminalId(), registration);
        }
        return loadedRegistrations;
    }

    private TerminalRegistration parseRegistration(String rawEntry, int position) {
        String[] fields = rawEntry.split("\\|", -1);
        if (fields.length != 4) {
            throw new IllegalStateException(
                    "Invalid robot-terminal.registry entry at position %d. Expected terminalId|terminalKey|vehicleId|regionCode."
                            .formatted(position)
            );
        }

        String terminalId = normalize(fields[0]);
        String terminalKey = normalize(fields[1]);
        if (terminalId == null || terminalKey == null) {
            throw new IllegalStateException(
                    "Invalid robot-terminal.registry entry at position %d. terminalId and terminalKey are required."
                            .formatted(position)
            );
        }

        return new TerminalRegistration(
                terminalId,
                terminalKey,
                normalize(fields[2]),
                normalize(fields[3])
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record TerminalRegistration(
            String terminalId,
            String terminalKey,
            String vehicleId,
            String regionCode
    ) {

        public boolean matchesKey(String requestedTerminalKey) {
            return terminalKey.equals(normalize(requestedTerminalKey));
        }

        private static String normalize(String value) {
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
