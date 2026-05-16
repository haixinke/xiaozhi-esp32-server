package xiaozhi.modules.pet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户画像表
 *
 * @author TDD
 * @version 1.0, 2025-05-16
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "user_profiles")
public class UserProfileEntity {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private String userId;

    /**
     * 画像内容
     */
    @TableField(value = "profile_content")
    private String profileContent;

    /**
     * 主题标签（JSON格式）
     */
    @TableField(value = "topics")
    private String topics;

    /**
     * 创建时间（ISO 8601格式字符串）
     */
    @TableField(value = "created_at")
    private String createdAt;

    /**
     * 更新时间（ISO 8601格式字符串）
     */
    @TableField(value = "updated_at")
    private String updatedAt;
}
