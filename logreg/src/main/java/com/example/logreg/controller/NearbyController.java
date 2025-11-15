package com.example.logreg.controller;

import com.example.logreg.dto.SuccessDTO;
import com.example.logreg.dto.LocationVO;
import com.example.logreg.dto.NearbyUserDTO;
import com.example.logreg.dto.OkDTO;
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
    public ResponseEntity<SuccessDTO<OkDTO>> updateLocation(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                        @RequestBody LocationVO body) {
        Double latitude = body == null ? null : body.getLatitude();
        Double longitude = body == null ? null : body.getLongitude();
        if (latitude == null || longitude == null) {
            return ResponseEntity.ok(new SuccessDTO<>(new OkDTO(false)));
        }
        Long userId = currentUserId(authorization);
        if (userId == null) {
            return ResponseEntity.ok(new SuccessDTO<>(new OkDTO(false)));
        }
        nearbyService.updateUserLocation(userId, latitude, longitude);
        return ResponseEntity.ok(new SuccessDTO<>(new OkDTO(true)));
    }

    @GetMapping("/users/nearby")
    public ResponseEntity<SuccessDTO<List<NearbyUserDTO>>> nearby(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                        @RequestParam(required = false) Double latitude,
                                                                        @RequestParam(required = false) Double longitude,
                                                                        @RequestParam(defaultValue = "10") Integer radiusKm,
                                                                        @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = currentUserId(authorization);
        if (latitude == null || longitude == null) {
            return ResponseEntity.ok(new SuccessDTO<>(java.util.Collections.emptyList()));
        }
        var list = nearbyService.findNearby(latitude, longitude, radiusKm, limit, userId);
        java.util.List<NearbyUserDTO> out = new java.util.ArrayList<>();
        for (var m : list) {
            NearbyUserDTO dto = new NearbyUserDTO(
                    (Long) m.get("userId"),
                    (String) m.get("username"),
                    (String) m.get("nickname"),
                    (Double) m.get("distanceKm")
            );
            out.add(dto);
        }
        return ResponseEntity.ok(new SuccessDTO<>(out));
    }

    @GetMapping("/users/nearby/me")
    public ResponseEntity<SuccessDTO<List<NearbyUserDTO>>> nearbyMe(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                                          @RequestParam(defaultValue = "10") Integer radiusKm,
                                                                          @RequestParam(defaultValue = "50") Integer limit) {
        Long userId = currentUserId(authorization);
        if (userId == null) {
            return ResponseEntity.ok(new SuccessDTO<>(java.util.Collections.emptyList()));
        }
        var list = nearbyService.findNearbyByMember(userId, radiusKm, limit);
        java.util.List<NearbyUserDTO> out = new java.util.ArrayList<>();
        for (var m : list) {
            NearbyUserDTO dto = new NearbyUserDTO(
                    (Long) m.get("userId"),
                    (String) m.get("username"),
                    (String) m.get("nickname"),
                    (Double) m.get("distanceKm")
            );
            out.add(dto);
        }
        return ResponseEntity.ok(new SuccessDTO<>(out));
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

    private Double asDouble(Object v) { return null; }
}