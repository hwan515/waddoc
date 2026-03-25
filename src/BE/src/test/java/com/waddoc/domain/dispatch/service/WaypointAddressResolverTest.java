package com.waddoc.domain.dispatch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class WaypointAddressResolverTest {

    private final WaypointAddressResolver resolver = new WaypointAddressResolver(
            new ObjectMapper(),
            new ClassPathResource("waypoint_address.json")
    );

    @Test
    void resolve_returnsWaypointForMappedAddress() {
        WaypointAddressResolver.ResolvedTarget target =
                resolver.resolve("경상북도 김천시 증산면 장전4길 14");

        assertThat(target.isMapped()).isTrue();
        assertThat(target.waypointNumber()).isEqualTo(59);
        assertThat(target.isDummyCompletionTarget()).isFalse();
    }

    @Test
    void resolve_returnsDummyTargetForUnmappedAddress() {
        WaypointAddressResolver.ResolvedTarget target =
                resolver.resolve("경상북도 김천시 증산면 테스트길 1");

        assertThat(target.isMapped()).isFalse();
        assertThat(target.waypointNumber()).isNull();
        assertThat(target.isDummyCompletionTarget()).isTrue();
    }
}
