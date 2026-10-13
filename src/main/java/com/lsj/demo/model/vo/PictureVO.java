package com.lsj.demo.model.vo;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.lsj.demo.model.entity.Picture;

import lombok.Data;
import org.springframework.beans.BeanUtils;
import java.util.Date;
import java.io.Serializable;
import java.util.List;

@Data
public class PictureVO implements Serializable {
  
    /**  
     * id  
     */  
    private Long id;  
  
    /**  
     * 图片 url  
     */  
    private String url;  

    /**
     * 缩略图 url（压缩后的 webp）
     * <p>
     * 列表页展示缩略图、详情页展示原图。
     * 历史数据可能没有这个字段，前端需要回退到 url。
     */
    private String thumbnailUrl;
  
    /**  
     * 图片名称  
     */  
    private String name;  
  
    /**  
     * 简介  
     */  
    private String introduction;  
  
    /**  
     * 标签  
     */  
    private List<String> tags;
  
    /**  
     * 分类  
     */  
    private String category;  
  
    /**  
     * 文件体积  
     */  
    private Long picSize;  
  
    /**  
     * 图片宽度  
     */  
    private Integer picWidth;  
  
    /**  
     * 图片高度  
     */  
    private Integer picHeight;  
  
    /**  
     * 图片比例  
     */  
    private Double picScale;  
  
    /**  
     * 图片格式  
     */  
    private String picFormat;  
  
    /**  
     * 用户 id  
     */  
    private Long userId;  
  
    /**  
     * 创建时间  
     */  
    private Date createTime;  
  
    /**  
     * 编辑时间  
     */  
    private Date editTime;  
  
    /**  
     * 更新时间  
     */  
    private Date updateTime;  

    /**
     * 审核状态：0-待审核; 1-通过; 2-拒绝
     * <p>
     * 前端「我的图片」用它在卡片角标上显示审核状态，
     * 缺了它角标会一直落在「未知」分支。
     */
    private Integer reviewStatus;

    /**  
     * 创建用户信息  
     */  
    private UserVO user;  
  
    private static final long serialVersionUID = 1L;  
  
    /**  
     * 封装类转对象
     *
     * @param pictureVO 图片封装类
     * @return 图片实体类
     */  
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {  
            return null;  
        }  
        Picture picture = new Picture();  
        BeanUtils.copyProperties(pictureVO, picture);
        // 类型不同，需要转换  
        picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        return picture;  
    }  
  
    /**  
     * 对象转封装类
     *
     * @param picture 图片实体类
     * @return 图片封装类
     */  
    public static PictureVO objToVo(Picture picture) {  
        if (picture == null) {  
            return null;  
        }  
        PictureVO pictureVO = new PictureVO();  
        BeanUtils.copyProperties(picture, pictureVO);  
        // 类型不同，需要转换  
        pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));  
        // 历史数据没有缩略图，回退成原图，避免前端列表出现裂图
        if (StrUtil.isBlank(pictureVO.getThumbnailUrl())) {
            pictureVO.setThumbnailUrl(picture.getUrl());
        }
        return pictureVO;  
    }  
}

