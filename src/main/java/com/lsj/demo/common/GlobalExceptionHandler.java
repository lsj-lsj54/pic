package com.lsj.demo.common;

import com.lsj.demo.exception.BusinessException;
import com.lsj.demo.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * 全局异常处理
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     *
     * @param e 业务异常
     * @return 统一响应，包含业务异常的错误码和信息
     */
    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    /**
     * 处理上传文件过大的异常
     * <p>
     * 这类异常原本会被下面的 RuntimeException 分支吞掉，只返回笼统的「系统错误」，
     * 让人误以为是后端挂了。这里单独处理，把体积限制直接告知前端。
     * 相关配置见 application.yml 的 spring.servlet.multipart。
     *
     * @param e 上传体积超限异常
     * @return 统一响应，说明是文件过大
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public BaseResponse<?> maxUploadSizeExceptionHandler(MaxUploadSizeExceededException e) {
        log.error("MaxUploadSizeExceededException: {}", e.getMessage());
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "上传文件过大，请压缩后重试");
    }

    /**
     * 处理运行时异常
     *
     * @param e 运行时异常
     * @return 统一响应，统一返回系统错误
     */
    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> runtimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}

