package com.example.activity.controller;

import com.example.activity.service.IpLocationsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.activity.dto.IpInfoDTO;
import com.example.activity.dto.GeoInfoDTO;

@RestController
@RequestMapping("/api")
@Tag(name="获取ip")
public class IPController {
    @Resource
    private IpLocationsService ipLocationsService;
    @RequestMapping("/activity/getip")
    public IpInfoDTO getIp(HttpServletRequest request){
        return ipLocationsService.getClientIp(request);
    }

    @RequestMapping("/activity/getgeo")
    public GeoInfoDTO getGeo(String ipAddress){
        return ipLocationsService.getGeoInfo(ipAddress);
    }


}
