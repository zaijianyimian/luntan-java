package com.example.activity.feign;

import com.example.activity.dto.UserBasicDTO;
import com.example.activity.dto.SuccessDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "User-Management", configuration = AuthForwardFeignConfig.class)
public interface UserServiceClient {
    @GetMapping("/api/users/basic")
    SuccessDTO<List<UserBasicDTO>> basics(@RequestParam("ids") String ids);
}
