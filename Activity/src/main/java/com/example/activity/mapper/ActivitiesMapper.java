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

    /**
     * 使用 Haversine 公式进行范围查询，返回指定半径内的活动列表（按距离升序，限制条数）
     */
    java.util.List<Activities> findNearby(
            @Param("longitude") double longitude,
            @Param("latitude") double latitude,
            @Param("radiusMeters") int radiusMeters,
            @Param("limit") int limit);
}




