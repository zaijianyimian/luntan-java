package com.example.logreg.generator.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.logreg.generator.domain.RelRolePerm;
import com.example.logreg.generator.service.RelRolePermService;
import com.example.logreg.generator.mapper.RelRolePermMapper;
import org.springframework.stereotype.Service;

/**
* @author lenovo
* @description 针对表【rel_role_perm(角色与权限关联表)】的数据库操作Service实现
* @createDate 2025-10-31 15:10:44
*/
@Service
public class RelRolePermServiceImpl extends ServiceImpl<RelRolePermMapper, RelRolePerm>
    implements RelRolePermService{

}




