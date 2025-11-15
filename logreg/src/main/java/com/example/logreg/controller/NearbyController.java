package com.example.logreg.controller;

import com.example.logreg.dto.SuccessDTO;
import com.example.logreg.service.NearbyService;
import com.example.logreg.config.JwtUtil;
import jakarta.annotation.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class NearbyController {

    @Resource
    private NearbyService nearbyService;

    @Resource
    private JwtUtil jwtUtil;

    @PostMapping("/user/location")
    public ResponseEntity<SuccessDTO<?>> updateLocation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestBody Map<String, Object> body) {
        Double latitude = asDouble(body.get("latitude"));
        Double longitude = asDouble(body.get("longitude"));
        if (latitude == null || longitude == null) {
            return ResponseEntity.ok(new SuccessDTO<>(Map.of("message", "latitude/longitude 不能为空")));
        }
        Long userId = currentUserId(authorization);
        if (userId == null) {
            return ResponseEntity.ok(new SuccessDTO<>(Map.of("message", "未认证")));
        }
        nearbyService.updateUserLocation(userId, latitude, longitude);
        return ResponseEntity.ok(new SuccessDTO<>(Map.of("ok", true)));
    }

    @GetMapping("/users/nearby")
    public ResponseEntity<SuccessDTO<List<Map<String, Object>>>> nearby(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                        @RequestParam(required = false) Double latitude,
                                                                        @RequestParam(required = false) Double longitude,
                                                                        @RequestParam(defaultValue = "10") Integer radiusKm,
                                                                        @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = currentUserId(authorization);
        if (latitude == null || longitude == null) {
            return ResponseEntity.ok(new SuccessDTO<>(java.util.Collections.emptyList()));
        }
        var list = nearbyService.findNearby(latitude, longitude, radiusKm, limit, userId);
        return ResponseEntity.ok(new SuccessDTO<>(list));
    }

    @GetMapping("/users/nearby/me")
    public ResponseEntity<SuccessDTO<List<Map<String, Object>>>> nearbyMe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                          @RequestParam(defaultValue = "10") Integer radiusKm,
                                                                          @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = currentUserId(authorization);
        if (userId == null) {
            return ResponseEntity.ok(new SuccessDTO<>(java.util.Collections.emptyList()));
        }
        var list = nearbyService.findNearbyByMember(userId, radiusKm, limit);
        return ResponseEntity.ok(new SuccessDTO<>(list));
    }

    private Long currentUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String jwt = authorization.substring(7);
        try {
            return jwtUtil.getUserId(jwt);
        } catch (Exception e) {
            return null;
        }
    }

    private Double asDouble(Object v) {
        if (v == null) return null;
        try { return Double.valueOf(String.valueOf(v)); } catch (Exception e) { return null; }
    }
}