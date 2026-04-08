package com.waddoc.global.security.authorization;

/**
 * 접근 제어 로직이 공통으로 사용할 최소 행위자 식별 정보다.
 */
public record AccessActor(String actorId, String actorRole) {
}
