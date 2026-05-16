package xiaozhi.modules.pet.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 记忆表
 *
 * @author TDD
 * @version 1.0, 2025-05-16
 * @since 1.0.0
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "memories")
public class MemoryEntity {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    @TableField(value = "user_id")
    private Long userId;

    /**
     * 智能体ID
     */
    @TableField(value = "agent_id")
    private String agentId;

    /**
     * 记忆文档内容
     */
    @TableField(value = "document")
    private String document;

    /**
     * 记忆分类
     */
    @TableField(value = "category")
    private String category;

    /**
     * 创建时间
     */
    @TableField(value = "created_at")
    private Date createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at")
    private Date updatedAt;
}
