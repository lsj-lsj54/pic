package com.lsj.demo.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 空间类型枚举
 * <p>
 * 私有空间：仅创建者可见可用；团队空间：创建者可邀请其他用户加入，
 * 成员按空间角色（viewer/editor/admin）获得不同权限。
 *
 * @author lsj
 */
@Getter
public enum SpaceTypeEnum {

    PRIVATE("私有", 0),
    TEAM("团队", 1);

    private final String text;

    private final int value;

    SpaceTypeEnum(String text, int value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     *
     * @param value 枚举值的 value
     * @return 枚举值，不存在时返回 null
     */
    public static SpaceTypeEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SpaceTypeEnum anEnum : SpaceTypeEnum.values()) {
            if (anEnum.value == value) {
                return anEnum;
            }
        }
        return null;
    }

    /**
     * 获取所有枚举的文本列表
     *
     * @return 文本列表
     */
    public static List<String> getAllTexts() {
        return Arrays.stream(SpaceTypeEnum.values())
                .map(SpaceTypeEnum::getText)
                .collect(Collectors.toList());
    }

    /**
     * 获取所有枚举的值列表
     *
     * @return 值列表
     */
    public static List<Integer> getAllValues() {
        return Arrays.stream(SpaceTypeEnum.values())
                .map(SpaceTypeEnum::getValue)
                .collect(Collectors.toList());
    }
}
