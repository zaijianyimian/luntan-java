package com.example.logreg.controller;

import com.example.logreg.config.DBUserDetailsManager;
import com.example.logreg.dto.BaseDto;
import com.example.logreg.dto.LoginDTO;
import com.example.logreg.dto.RegisterVO;
import com.example.logreg.dto.SuccessDTO;
import com.example.logreg.dto.UserInfoDTO;
import com.example.logreg.dto.ChangePassVO;
import com.example.logreg.dto.extend.*;
import com.example.logreg.generator.domain.securitydomconf.SysUserDetails;
import com.example.logreg.generator.mapper.SysUserMapper;
import com.example.logreg.generator.domain.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.logreg.config.JwtUtil;
import com.example.logreg.service.NearbyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@Tag(name = "用户登录注册接口")
@RequestMapping("/api")
@Slf4j
public class LogRegController {
    @Resource
    private AuthenticationManager authenticationManager;
    @Resource
    private JwtUtil jwtUtil;
    @Resource
    private DBUserDetailsManager dbUserDetailsManager;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private NearbyService nearbyService;

    @PostMapping("/user/login")
    public ResponseEntity<BaseDto> login(@RequestBody LoginDTO loginData) {
        String username = loginData.getUsername();
        String password = loginData.getPassword();

        SysUser u = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("username", username).eq("deleted", 0));
        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
            return ResponseEntity.ok(new LoginFailureDto());
        }
        if (!passwordEncoder.matches(password, u.getPassword())) {
            return ResponseEntity.ok(new LoginFailureDto());
        }

        String token = jwtUtil.generateToken(u.getId());
        if (loginData.getLatitude() != null && loginData.getLongitude() != null) {
            nearbyService.updateUserLocation(u.getId(), loginData.getLatitude(), loginData.getLongitude());
        }
        return ResponseEntity.ok(new LoginSuccessDto(token));
    }



    //测试接口
    @GetMapping("/user/info")
    public ResponseEntity<SuccessDTO<UserInfoDTO>> userInfo(Authentication authentication) {
        java.util.List<String> roles = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
        UserInfoDTO dto = new UserInfoDTO("用户信息获取成功", authentication.getName(), roles);
        return ResponseEntity.ok(new SuccessDTO<>(dto));
    }

    @PostMapping("/user/register")
    @Operation(summary = "用户注册")
    public ResponseEntity<BaseDto> register(@RequestBody RegisterVO registerData){
        log.error("用户注册");
        String username = registerData.getUsername();
        String password = registerData.getPassword();
        String email = registerData.getEmail();

        if(username == null || username.trim().isEmpty()){
            return ResponseEntity.ok(new RegisterFailureDto("用户名不能为空"));
        }
        if(dbUserDetailsManager.userExists(username)){
            return  ResponseEntity.ok(new RegisterFailureDto("用户已存在"));
        }
        SysUserDetails userDetails = new SysUserDetails(username,password,email,null);
        try{
            dbUserDetailsManager.createUser(userDetails);
            return ResponseEntity.ok(new RegisterSuccessDto());
        }catch (RuntimeException e){
            return ResponseEntity.ok(new RegisterFailureDto(e.getMessage()));
        }
    }
    @PostMapping("/user/changepass")
    @Operation(summary = "修改密码")
    public ResponseEntity<BaseDto> changePass(@RequestHeader("Authorization") String authorization,
                                              @RequestBody ChangePassVO changePassVO){
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : null;
        if (token == null) {
            return ResponseEntity.ok(new RegisterFailureDto("未提供token"));
        }
        Long userId;
        try {
            userId = jwtUtil.getUserId(token);
        } catch (Exception e) {
            return ResponseEntity.ok(new RegisterFailureDto("token无效"));
        }
        SysUser u = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("id", userId).eq("deleted", 0));
        if (u == null || u.getStatus() == null || u.getStatus() != 1) {
            return ResponseEntity.ok(new RegisterFailureDto("用户不存在或被禁用"));
        }
        String oldPassword = changePassVO.getOldPassword();
        String newPassword = changePassVO.getNewPassword();
        if (oldPassword == null || oldPassword.trim().isEmpty()) {
            return ResponseEntity.ok(new RegisterFailureDto("旧密码不能为空"));
        }
        if (!passwordEncoder.matches(oldPassword, u.getPassword())) {
            return ResponseEntity.ok(new RegisterFailureDto("旧密码错误"));
        }
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.ok(new RegisterFailureDto("新密码不能为空"));
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        sysUserMapper.updateById(u);
        return ResponseEntity.ok(new ChangeSuccessDto());
    }
}
