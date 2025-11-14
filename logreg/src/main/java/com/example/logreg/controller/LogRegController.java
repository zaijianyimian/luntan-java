package com.example.logreg.controller;

import com.example.logreg.config.DBUserDetailsManager;
import com.example.logreg.dto.BaseDto;
import com.example.logreg.dto.VerifyDTO;
import com.example.logreg.dto.extend.*;
import com.example.logreg.generator.domain.securitydomconf.SysUserDetails;
import com.example.logreg.config.JwtUtil;
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

    @PostMapping("/user/login")
    public ResponseEntity<BaseDto> login(@RequestBody VerifyDTO loginData) {
        String username = loginData.getEmail();
        String password = loginData.getCode();

        log.error("用户登录账号：" + username + "，密码：" + password);

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(username, password)
            );

            String token = jwtUtil.generateToken(username);
            return ResponseEntity.ok(new LoginSuccessDto(token));

        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "用户名或密码错误");
            errorResponse.put("message", "登录失败");

            return ResponseEntity.ok(new LoginFailureDto());
        }
    }



    //测试接口
    @GetMapping("/user/info")
    public Map<String, Object> userInfo(Authentication authentication) {
        Map<String, Object> map = new HashMap<>();
        map.put("msg", "用户信息获取成功");
        map.put("username", authentication.getName());
        map.put("roles", authentication.getAuthorities());
        return map;
    }

    @PostMapping("/user/register")
    @Operation(summary = "用户注册")
    public ResponseEntity<BaseDto> register(@RequestBody Map<String,String> registerData){
        log.error("用户注册");
        String username = registerData.get("username");
        String password = registerData.get("password");
        String email = registerData.get("email");

        if(username == null || username.trim().isEmpty()){
            return ResponseEntity.ok(new RegisterFailureDto("用户名不能为空"));
        }
        if(dbUserDetailsManager.userExists(username)){
            return  ResponseEntity.ok(new RegisterFailureDto("用户已存在"));
        }
        SysUserDetails userDetails = new SysUserDetails(username,password,email,null);
        dbUserDetailsManager.createUser(userDetails);
        return ResponseEntity.ok(new RegisterSuccessDto());
    }
    @PostMapping("/user/changepass")
    @Operation(summary = "修改密码")
    public ResponseEntity<BaseDto> changePass(@RequestBody Map<String,String> changePassData){
        log.error("修改密码");
        //获取参数
        String username = changePassData.get("username");
        String oldPassword = changePassData.get("oldPassword");
        String newPassword = changePassData.get("newPassword");
        //验证参数
        if(username == null || username.trim().isEmpty()){
            return ResponseEntity.ok(new RegisterFailureDto("用户名不能为空"));
        }
        if(!dbUserDetailsManager.userExists(username)){
            return  ResponseEntity.ok(new RegisterFailureDto("用户不存在"));
        }
        // 不再强转为 SysUserDetails，按接口使用
        UserDetails userDetails = dbUserDetailsManager.loadUserByUsername(username);
        if(!passwordEncoder.matches(oldPassword, userDetails.getPassword())){
            return ResponseEntity.ok(new RegisterFailureDto("旧密码错误"));
        }
        if(newPassword == null || newPassword.trim().isEmpty()){
            return ResponseEntity.ok(new RegisterFailureDto("新密码不能为空"));
        }
        dbUserDetailsManager.changePassword(oldPassword,newPassword);
        return ResponseEntity.ok(new ChangeSuccessDto());
    }
}
