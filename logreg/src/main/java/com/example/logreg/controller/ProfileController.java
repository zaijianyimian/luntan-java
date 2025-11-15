package com.example.logreg.controller;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.example.logreg.config.JwtUtil;
import com.example.logreg.dto.SuccessDTO;
import com.example.logreg.dto.AvatarUploadDTO;
import com.example.logreg.dto.NicknameVO;
import com.example.logreg.dto.OkDTO;
import com.example.logreg.dto.UserBasicDTO;
import com.example.logreg.generator.domain.SysUser;
import com.example.logreg.generator.mapper.SysUserMapper;
import com.example.logreg.service.AvatarService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@RestController
@Slf4j
@RequestMapping("/api")
public class ProfileController {

    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private SysUserMapper sysUserMapper;
    @Resource
    private AvatarService avatarService;


    /**
     * 上传头像
     * @param authorization
     * @param file
     * @return
     */
    @PostMapping(value = "/user/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SuccessDTO<AvatarUploadDTO>> uploadAvatar(@RequestHeader("Authorization") String authorization,
                                                      @RequestParam("file") MultipartFile file) {
        Long userId = currentUserId(authorization);
        if (userId == null || file == null || file.isEmpty()) {
            return ResponseEntity.ok(new SuccessDTO<>(new AvatarUploadDTO(false, null, "缺少用户或文件")));
        }
        try {
            String url = avatarService.putAvatar(userId, file.getContentType(), file.getInputStream(), file.getSize());
            String objectName = userId + ".jpg";
            UpdateWrapper<SysUser> uw = new UpdateWrapper<>();
            uw.eq("id", userId).set("photo_url", objectName);
            sysUserMapper.update(null, uw);
            return ResponseEntity.ok(new SuccessDTO<>(new AvatarUploadDTO(true, url, null)));
        } catch (Exception e) {
            log.error("avatar upload failed", e);
            String msg = (e instanceof IllegalStateException) ? "存储配置缺失" : "存储失败";
            return ResponseEntity.ok(new SuccessDTO<>(new AvatarUploadDTO(false, null, msg)));
        }
    }

    @PostMapping("/user/nickname")
    public ResponseEntity<SuccessDTO<OkDTO>> setNickname(@RequestHeader("Authorization") String authorization,
                                                     @RequestBody NicknameVO body) {
        Long userId = currentUserId(authorization);
        String nickname = body == null ? null : body.getNickname();
        if (userId == null || nickname == null || nickname.isBlank()) {
            return ResponseEntity.ok(new SuccessDTO<>(new OkDTO(false)));
        }
        UpdateWrapper<SysUser> uw = new UpdateWrapper<>();
        uw.eq("id", userId).set("nickname", nickname.trim());
        sysUserMapper.update(null, uw);
        return ResponseEntity.ok(new SuccessDTO<>(new OkDTO(true)));
    }

    private Long currentUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        String jwt = authorization.substring(7);
        try { return jwtUtil.getUserId(jwt); } catch (Exception e) { return null; }
    }

    @GetMapping("/users/basic")
    public ResponseEntity<SuccessDTO<java.util.List<UserBasicDTO>>> basic(@RequestParam("ids") String ids) {
        java.util.List<Long> idList = java.util.Arrays.stream(ids.split(",")).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
        java.util.List<SysUser> users = idList.isEmpty() ? java.util.Collections.emptyList() : sysUserMapper.selectBatchIds(idList);
        java.util.List<UserBasicDTO> out = new java.util.ArrayList<>();
        for (SysUser u : users == null ? java.util.Collections.<SysUser>emptyList() : users) {
            String obj = u.getPhotoUrl();
            String url = null;
            try { if (obj != null && !obj.isBlank()) url = avatarService.presignedUrl(obj); } catch (Exception ignore) {}
            out.add(new UserBasicDTO(u.getId(), u.getUsername(), obj, url));
        }
        return ResponseEntity.ok(new SuccessDTO<>(out));
    }
}