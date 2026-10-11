package com.lsj.demo.controller;
import com.lsj.demo.common.BaseResponse;
import com.lsj.demo.common.ResultUtils;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/")
public class MainController {

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public BaseResponse<String> health() {
        return ResultUtils.success("ok");
    }

}

