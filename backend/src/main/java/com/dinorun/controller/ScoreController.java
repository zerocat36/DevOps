package com.dinorun.controller;

import com.dinorun.service.ScoreService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class ScoreController {

    private final ScoreService scoreService;

    public ScoreController(ScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * 게임 오버 시 점수 저장
     * POST /api/scores
     * Body: { "nickname": "PLAYER_01", "score": 1234 }
     */
    @PostMapping("/scores")
    public Map<String, Object> saveScore(@RequestBody Map<String, Object> body) {
        String nickname = (String) body.get("nickname");
        int score = (int) body.get("score");

        boolean isNewBest = scoreService.saveScore(nickname, score);

        return Map.of(
            "success",  true,
            "newBest",  isNewBest,
            "nickname", nickname,
            "score",    score
        );
    }

    /**
     * 상위 랭킹 조회
     * GET /api/ranking?limit=10
     */
    @GetMapping("/ranking")
    public List<Map<String, Object>> getRanking(
            @RequestParam(defaultValue = "10") int limit) {
        return scoreService.getTopRanking(limit);
    }

    /**
     * 실시간 랭킹 SSE 스트림
     * GET /api/ranking/stream
     *
     * 점수가 저장될 때마다 최신 랭킹을 push
     * Vue의 RankingPanel.vue 가 이 스트림을 구독
     */
    @GetMapping(value = "/ranking/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter rankingStream() {
        return scoreService.createEmitter();
    }
}