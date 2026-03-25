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

@Component
public class WaypointAddressResolver {

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
        return new ResolvedTarget(waypointNumberByNormalizedAddress.get(normalize(address)));
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
