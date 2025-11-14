package com.example.activity.controller;

import com.example.activity.service.IpLocationsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name="获取ip")
public class IPController {
    @Resource
    private IpLocationsService ipLocationsService;
    @RequestMapping("/getip")
    public Map<String, Object> getIp(HttpServletRequest request){
        return ipLocationsService.getClientIp(request);
    }

    @RequestMapping("/getgeo")
    public Map<String, Object> getGeo(String ipAddress){
        return ipLocationsService.getGeoInfo(ipAddress);
    }


}
