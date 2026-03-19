package com.dinorun.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ScoreService {

    // Redis Sorted Set 키 이름
    private static final String RANKING_KEY = "dino:ranking";

    // Redis leader (쓰기용)
    private final StringRedisTemplate leaderRedis;

    // Redis follower (읽기용) — 없으면 leader 재사용
    private final StringRedisTemplate followerRedis;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // SSE emitter 목록 (스레드 안전)
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public ScoreService(StringRedisTemplate leaderRedis,
                        StringRedisTemplate followerRedis) {
        this.leaderRedis  = leaderRedis;
        this.followerRedis = followerRedis;
    }

    /**
     * 점수 저장
     * Redis Sorted Set: ZADD dino:ranking score nickname
     * 동일 닉네임은 최고 점수만 유지 (낮은 점수로 덮어쓰지 않음)
     *
     * @return 개인 최고 점수 갱신 여부
     */
    public boolean saveScore(String nickname, int score) {
        ZSetOperations<String, String> zset = leaderRedis.opsForZSet();

        // 현재 저장된 점수 조회
        Double current = zset.score(RANKING_KEY, nickname);
        boolean isNewBest = current == null || score > current;

        if (isNewBest) {
            // ZADD — score 를 double 로 저장 (Redis ZSet 특성)
            zset.add(RANKING_KEY, nickname, score);
        }

        // 점수 저장 후 모든 SSE 클라이언트에 최신 랭킹 push
        broadcastRanking();

        return isNewBest;
    }

    /**
     * 상위 N명 랭킹 조회
     * ZREVRANGEBYSCORE — 높은 점수 순 정렬
     */
    public List<Map<String, Object>> getTopRanking(int limit) {
        ZSetOperations<String, String> zset = followerRedis.opsForZSet();

        // 높은 점수 → 낮은 점수 순으로 상위 limit 개 조회
        Set<ZSetOperations.TypedTuple<String>> tuples =
            zset.reverseRangeWithScores(RANKING_KEY, 0, limit - 1);

        if (tuples == null) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> t : tuples) {
            result.add(Map.of(
                "nickname", t.getValue(),
                "score",    t.getScore() != null ? t.getScore().intValue() : 0
            ));
        }
        return result;
    }

    /**
     * SSE Emitter 생성 및 등록
     * 연결 즉시 현재 랭킹을 한 번 전송
     */
    public SseEmitter createEmitter() {
        SseEmitter emitter = new SseEmitter(0L);  // 타임아웃 없음
        emitters.add(emitter);

        // 연결되자마자 현재 랭킹 즉시 전송
        try {
            emitter.send(
                SseEmitter.event().data(
                    objectMapper.writeValueAsString(getTopRanking(10)),
                    MediaType.APPLICATION_JSON
                )
            );
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(()    -> emitters.remove(emitter));
        emitter.onError(e       -> emitters.remove(emitter));

        return emitter;
    }

    /**
     * 모든 SSE 클라이언트에 최신 랭킹 broadcast
     * saveScore() 호출 후 자동 실행
     */
    private void broadcastRanking() {
        List<Map<String, Object>> ranking = getTopRanking(10);

        String json;
        try {
            json = objectMapper.writeValueAsString(ranking);
        } catch (Exception e) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(
                    SseEmitter.event().data(json, MediaType.APPLICATION_JSON)
                );
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}