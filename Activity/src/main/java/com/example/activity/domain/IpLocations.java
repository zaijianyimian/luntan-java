package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @TableName ip_locations
 */
@TableName(value ="ip_locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpLocations {
    private String ip;

    private String country;

    private String region;

    private String city;

    private BigDecimal latitude;

    private BigDecimal longitude;
}