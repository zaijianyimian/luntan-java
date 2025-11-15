package com.example.filter.config;


import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.example.filter.generator.mapper.SysUserMapper;
import com.example.filter.generator.domain.SysUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.PrintWriter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

     @Resource
    private JwtUtil jwtUtil;  // 这个应该不为null

    @Autowired
    private DBUserDetailsManager dbUserDetailsManager;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String jwt = authHeader.substring(7);
            
            Long userId = null;
            try {
                userId = jwtUtil.getUserId(jwt);
            } catch (Exception e) {
                response.setStatus(401);
                response.setContentType("application/json;charset=UTF-8");
                try (PrintWriter out = response.getWriter()) {
                    out.write("{\"code\":401,\"message\":\"" + e.getMessage() + "\"}");
                }
                return;
            }
            if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                SysUser sysUser = sysUserMapper.selectOne(new QueryWrapper<SysUser>().eq("id", userId).eq("deleted", 0));
                if (sysUser == null) {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    try (PrintWriter out = response.getWriter()) {
                        out.write("{\"code\":401,\"message\":\"invalid user\"}");
                    }
                    return;
                }
                UserDetails userDetails = dbUserDetailsManager.loadUserByUsername(sysUser.getUsername());
                if (jwtUtil.validateToken(jwt)) {
                    UsernamePasswordAuthenticationToken authenticationToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null,
                                    userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}

