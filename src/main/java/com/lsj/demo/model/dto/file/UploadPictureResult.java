package com.lsj.demo.model.dto.file;

import lombok.Data;

@Data
public class UploadPictureResult {  
  
    /**  
     * 图片地址（原图）
     */  
    private String url;  

    /**
     * 缩略图地址（压缩后的 webp）
     * <p>
     * 列表页展示缩略图、点进详情再看原图，可以明显减少首页流量。
     * 压缩失败时会回退成原图地址，不会为空。
     */
    private String thumbnailUrl;

    /**  
     * 图片名称  
     */  
    private String picName;  
  
    /**  
     * 文件体积  
     */  
    private Long picSize;  
  
    /**  
     * 图片宽度  
     */  
    private int picWidth;  
  
    /**  
     * 图片高度  
     */  
    private int picHeight;  
  
    /**  
     * 图片宽高比  
     */  
    private Double picScale;  
  
    /**  
     * 图片格式  
     */  
    private String picFormat;  
  
}
