package com.waddoc.domain.dispatch.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 웨이포인트 번호를 운영 화면에 노출할 목적지 주소로 해석한다.
 */
@Component
public class WaypointAddressResolver {

    private static final String LOCAL_SEED_TOPOLOGY_ADDRESS_PREFIX = "경상북도김천시증산면위상지도웨이포인트";
    private static final int LOCAL_SEED_WAYPOINT_START = 1;
    private static final int LOCAL_SEED_WAYPOINT_END = 229;
    private static final String LOCAL_SEED_REALISTIC_ADDRESS_PREFIX = "경상북도김천시증산면";
    private static final int LOCAL_SEED_BUILDING_NUMBER_OFFSET = 3;
    private static final List<String> LOCAL_SEED_ROAD_NAMES = List.of(
            "황항길",
            "평촌길",
            "유성길",
            "수도길",
            "송하길",
            "가례길",
            "금곡길",
            "모산길",
            "삼도봉로",
            "증산로",
            "하강길",
            "부항길"
    );

    private final Map<String, Integer> waypointNumberByNormalizedAddress;

    public WaypointAddressResolver(
            ObjectMapper objectMapper,
            @Value("${dispatch.waypoint-mapping-resource:classpath:waypoint_address.json}") Resource mappingResource
    ) {
        this.waypointNumberByNormalizedAddress = loadMappings(objectMapper, mappingResource);
    }

    public ResolvedTarget resolve(String address) {
        if (address == null || address.isBlank()) {
            return new ResolvedTarget(null);
        }

        String normalizedAddress = normalize(address);
        Integer mappedWaypointNumber = waypointNumberByNormalizedAddress.get(normalizedAddress);
        if (mappedWaypointNumber != null) {
            return new ResolvedTarget(mappedWaypointNumber);
        }

        return new ResolvedTarget(resolveLocalSeedWaypointNumber(normalizedAddress));
    }

    private Map<String, Integer> loadMappings(ObjectMapper objectMapper, Resource mappingResource) {
        try (InputStream inputStream = mappingResource.getInputStream()) {
            MappingDocument document = objectMapper.readValue(inputStream, MappingDocument.class);
            List<MappingEntry> entries = document.mapping() == null ? List.of() : document.mapping();
            return entries.stream()
                    .filter(entry -> entry.address() != null && entry.waypointNumber() != null)
                    .collect(Collectors.toUnmodifiableMap(
                            entry -> normalize(entry.address()),
                            MappingEntry::waypointNumber,
                            (left, right) -> left
                    ));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load waypoint address mappings.", e);
        }
    }

    private String normalize(String address) {
        return address == null ? null : address.replaceAll("\\s+", "").trim();
    }

    private Integer resolveLocalSeedWaypointNumber(String normalizedAddress) {
        if (normalizedAddress == null) {
            return null;
        }

        if (normalizedAddress.startsWith(LOCAL_SEED_TOPOLOGY_ADDRESS_PREFIX)) {
            String suffix = normalizedAddress.substring(LOCAL_SEED_TOPOLOGY_ADDRESS_PREFIX.length());
            if (!suffix.matches("\\d+")) {
                return null;
            }

            int waypointNumber = Integer.parseInt(suffix);
            if (waypointNumber < LOCAL_SEED_WAYPOINT_START || waypointNumber > LOCAL_SEED_WAYPOINT_END) {
                return null;
            }
            return waypointNumber;
        }

        if (!normalizedAddress.startsWith(LOCAL_SEED_REALISTIC_ADDRESS_PREFIX)) {
            return null;
        }

        String suffix = normalizedAddress.substring(LOCAL_SEED_REALISTIC_ADDRESS_PREFIX.length());
        for (int roadIndex = 0; roadIndex < LOCAL_SEED_ROAD_NAMES.size(); roadIndex++) {
            String normalizedRoadName = normalize(LOCAL_SEED_ROAD_NAMES.get(roadIndex));
            if (!suffix.startsWith(normalizedRoadName)) {
                continue;
            }

            String buildingNumberValue = suffix.substring(normalizedRoadName.length());
            if (!buildingNumberValue.matches("\\d+")) {
                return null;
            }

            int buildingNumber = Integer.parseInt(buildingNumberValue);
            int waypointNumber = ((buildingNumber - LOCAL_SEED_BUILDING_NUMBER_OFFSET) * LOCAL_SEED_ROAD_NAMES.size())
                    + roadIndex
                    + 1;
            if (waypointNumber < LOCAL_SEED_WAYPOINT_START || waypointNumber > LOCAL_SEED_WAYPOINT_END) {
                return null;
            }
            return waypointNumber;
        }

        return null;
    }

    public record ResolvedTarget(Integer waypointNumber) {
        public boolean isMapped() {
            return waypointNumber != null;
        }

        public boolean isDummyCompletionTarget() {
            return waypointNumber == null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MappingDocument(List<MappingEntry> mapping) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MappingEntry(String id, Integer waypointNumber, String address) {
    }
}
