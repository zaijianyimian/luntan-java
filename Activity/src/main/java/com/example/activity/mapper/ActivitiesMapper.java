package com.example.activity.mapper;

import com.example.activity.domain.Activities;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author lenovo
* @description 针对表【activities】的数据库操作Mapper
* @createDate 2025-11-08 13:55:41
* @Entity .domain.Activities
*/
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface ActivitiesMapper extends BaseMapper<Activities> {

    @Update("UPDATE activities SET location = ST_SRID(Point(#{longitude}, #{latitude}), 4326) WHERE id = #{id}")
    int updateLocationPoint(@Param("id") Integer id,
                            @Param("longitude") java.math.BigDecimal longitude,
                            @Param("latitude") java.math.BigDecimal latitude);
}




