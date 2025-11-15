package com.example.logreg.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.logreg.config.JwtUtil;
import com.example.logreg.dto.SuccessDTO;
import com.example.logreg.generator.domain.SysUser;
import com.example.logreg.generator.mapper.SysUserMapper;
import com.example.logreg.service.AvatarService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProfileController {

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private AvatarService avatarService;

    @PostMapping(value = "/user/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDTO<?>> uploadAvatar(@RequestHeader("Authorization") String authorization,
                                                      @RequestParam("file") MultipartFile file) throws Exception {
        Long userId = currentUserId(authorization);
        if (userId == null || file == null || file.isEmpty()) {
            return ResponseEntity.ok(new SuccessDTO<>(Map.of("ok", false)));
        }
        String url = avatarService.putAvatar(userId, file.getContentType(), file.getInputStream(), file.getSize());
        UpdateWrapper<SysUser> uw = new UpdateWrapper<>();
        uw.eq("id", userId).set("avatar_url", url);
        sysUserMapper.update(null, uw);
        return ResponseEntity.ok(new SuccessDTO<>(Map.of("ok", true, "url", url)));
    }

    @PostMapping("/user/nickname")
    public ResponseEntity<SuccessDTO<?>> setNickname(@RequestHeader("Authorization") String authorization,
                                                     @RequestBody Map<String, String> body) {
        Long userId = currentUserId(authorization);
        String nickname = body == null ? null : body.get("nickname");
        if (userId == null || nickname == null || nickname.isBlank()) {
            return ResponseEntity.ok(new SuccessDTO<>(Map.of("ok", false)));
        }
        UpdateWrapper<SysUser> uw = new UpdateWrapper<>();
        uw.eq("id", userId).set("nickname", nickname.trim());
        sysUserMapper.update(null, uw);
        return ResponseEntity.ok(new SuccessDTO<>(Map.of("ok", true)));
    }

    private Long currentUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String jwt = authorization.substring(7);
        try { return jwtUtil.getUserId(jwt); } catch (Exception e) { return null; }
    }
}