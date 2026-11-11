package com.lsj.demo.websocket;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lsj.demo.model.dto.websocket.PictureEditRequestMessage;
import com.lsj.demo.model.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * 图片编辑事件生产者，把 WebSocket 收到的消息发布到 Disruptor RingBuffer
 *
 * @author lsj
 */
@Slf4j
@Component
public class PictureEditEventProducer {

    @Resource
    private Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    public void publishEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取可以生成的位置
        long next = ringBuffer.next();
        try {
            PictureEditEvent pictureEditEvent = ringBuffer.get(next);
            pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
            pictureEditEvent.setSession(session);
            pictureEditEvent.setUser(user);
            pictureEditEvent.setPictureId(pictureId);
        } finally {
            // 发布事件（无论中间是否出错都要 publish，避免 RingBuffer 序号被占用后不释放）
            ringBuffer.publish(next);
        }
    }

    /**
     * 优雅停机
     */
    @PreDestroy
    public void close() {
        pictureEditEventDisruptor.shutdown();
    }
}
