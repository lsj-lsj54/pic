package com.lsj.demo.websocket;

import com.lsj.demo.model.dto.websocket.PictureEditRequestMessage;
import com.lsj.demo.model.entity.User;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * 图片编辑事件，作为 Disruptor RingBuffer 中传递的数据对象
 *
 * @author lsj
 */
@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;

    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;
}
