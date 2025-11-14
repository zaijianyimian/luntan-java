package com.example.filter.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.filter.generator.domain.RelRolePerm;
import com.example.filter.generator.domain.RelUserRole;
import com.example.filter.generator.domain.SysPermission;
import com.example.filter.generator.domain.SysUser;
import com.example.filter.generator.domain.securitydomconf.SysUserDetails;
import com.example.filter.generator.mapper.RelRolePermMapper;
import com.example.filter.generator.mapper.RelUserRoleMapper;
import com.example.filter.generator.mapper.SysPermissionMapper;
import com.example.filter.generator.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;


@Component
@Primary
@Lazy
public class DBUserDetailsManager implements UserDetailsManager {
    @Resource
    private SysUserMapper sysUserMapper;//用户表
    @Resource
    private RelUserRoleMapper relUserRoleMapper;//用户角色关联表
    @Resource
    private RelRolePermMapper relRolePermMapper;//角色权限关联表
    @Resource
    private SysPermissionMapper sysPermissionMapper;
    @Resource
    private PasswordEncoder passwordEncoder;


    @Override
    public void createUser(UserDetails user) {
        if (user instanceof SysUserDetails) {
            SysUserDetails sysUserDetails = (SysUserDetails) user;

            // 查询用户名是否重复
            QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", user.getUsername()).eq("deleted", 0);

            SysUser sysUser = new SysUser();
            sysUser.setUsername(sysUserDetails.getUsername());
            sysUser.setPassword(passwordEncoder.encode(sysUserDetails.getPassword()));
            sysUser.setEmail(sysUserDetails.getEmail());
            sysUser.setCreateAt(LocalDateTime.now());

            try {
                sysUserMapper.insert(sysUser);
            } catch (DuplicateKeyException e) {
                throw new RuntimeException("用户名已存在");
            }

            RelUserRole relUserRole = new RelUserRole();
            relUserRole.setUserId(sysUser.getId());
            relUserRole.setRoleId(2L); // 默认角色为普通用户
            relUserRoleMapper.insert(relUserRole);
        }
    }


    @Override
    public void updateUser(UserDetails user) {
        if (user instanceof SysUserDetails) {
            SysUserDetails userDetails = (SysUserDetails) user;
            // 根据用户名查找用户ID
            QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", userDetails.getUsername()).eq("deleted", 0);
            SysUser sysUser = sysUserMapper.selectOne(queryWrapper);

            if (sysUser != null) {
                // 设置新密码并更新
                sysUser.setPassword(passwordEncoder.encode(userDetails.getPassword()));
                sysUserMapper.updateById(sysUser); // 使用 updateById 更新
            } else {
                throw new UsernameNotFoundException("用户不存在");
            }
        }
    }


    @Override
    public void deleteUser(String username) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username);
        SysUser user = sysUserMapper.selectOne(queryWrapper);
        if (user != null) {
            // 逻辑删除用户
            user.setDeleted(1);
            sysUserMapper.updateById(user);
        } else {
            throw new UsernameNotFoundException("用户不存在");
        }
    }

    @Override
    public void changePassword(String oldPassword, String newPassword) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", SecurityContextHolder.getContext().getAuthentication().getName());
        SysUser user = sysUserMapper.selectOne(queryWrapper);
        if (user != null) {
            user.setPassword(passwordEncoder.encode(newPassword));
            sysUserMapper.updateById(user);
        }else {
            throw new UsernameNotFoundException("用户不存在");
        }
    }

    @Override
    public boolean userExists(String username) {
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).eq("deleted",0);
        return sysUserMapper.selectCount(queryWrapper) > 0;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        //查询未删除用户
        QueryWrapper<SysUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username).eq("deleted",0);
        SysUser user = sysUserMapper.selectOne(queryWrapper);// 查询用户

        if(user == null){
            throw new UsernameNotFoundException("用户不存在");
        }

        else{
            //判断账号状态
            //用户是否被禁用或不存在
            boolean enabled = user.getStatus() != null && user.getStatus() == 1;//账号状态，1为正常，0为禁用
            //账号是否被锁
            boolean accountExpired = false; //账号是否过期
            //用户密码是否过期
            boolean credentialsExpired = false; //密码是否过期
            //用户是否被锁
            boolean accountLocked = false;

            //查询用户的角色列表
            Long id = user.getId();
            QueryWrapper<RelUserRole> roleQueryWrapper = new QueryWrapper<>();
            roleQueryWrapper.select("role_id").eq("user_id", id);
            List<Long> roleIds = relUserRoleMapper.selectList(roleQueryWrapper)
                    .stream().map(RelUserRole::getRoleId).toList();
            QueryWrapper<RelRolePerm> relRolePermQueryWrapper = new QueryWrapper<>();
            relRolePermQueryWrapper.select("perm_id").in("role_id", roleIds);
            //查询用户权限列表
            List<Long> permIds = relRolePermMapper.selectList(relRolePermQueryWrapper)
                    .stream().map(RelRolePerm::getPermId).toList();
            QueryWrapper<SysPermission> permQueryWrapper = new QueryWrapper<>();
            permQueryWrapper.select("code", "name").in("id", permIds);
            List<String> permissions = sysPermissionMapper.selectList(permQueryWrapper)
                    .stream().map(SysPermission::getCode).toList();
            //返回用户信息
            return User.withUsername(user.getUsername())
                    .password(user.getPassword())
                    .disabled(!enabled)
                    .accountLocked(accountLocked)
                    .accountExpired(accountExpired)
                    .credentialsExpired(credentialsExpired)
                    .authorities(permissions.isEmpty() ? new String[]{"NO_AUTH"} : permissions.toArray(String[]::new))
                    .build();
        }
    }
}
