package com.lsj.demo.conifg;

import com.lmax.disruptor.dsl.Disruptor;
import com.lsj.demo.websocket.PictureEditEvent;
import com.lsj.demo.websocket.PictureEditEventWorkHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片编辑事件 Disruptor 配置
 * <p>
 * 用一个 RingBuffer + WorkerPool 消费 WebSocket 编辑消息，把消息处理从
 * WebSocket 的收发线程里解耦出来，避免高频编辑动作阻塞连接线程。
 *
 * @author lsj
 */
@Configuration
public class PictureEditEventDisruptorConfig {

    @Resource
    private PictureEditEventWorkHandler pictureEditEventWorkHandler;

    @Bean("pictureEditEventDisruptor")
    public Disruptor<PictureEditEvent> pictureEditEventDisruptor() {
        // ringBuffer 的大小，必须是 2 的 N 次方
        int bufferSize = 1024 * 256;
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger index = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "pictureEditEventDisruptor-" + index.getAndIncrement());
                thread.setDaemon(false);
                return thread;
            }
        };
        Disruptor<PictureEditEvent> disruptor = new Disruptor<>(
                PictureEditEvent::new,
                bufferSize,
                threadFactory
        );
        // 设置消费者（WorkerPool 允许多个 worker 并发消费，PictureEditEventWorkHandler 内部按图片 id 隔离状态）
        disruptor.handleEventsWithWorkerPool(pictureEditEventWorkHandler);
        // 开启 disruptor
        disruptor.start();
        return disruptor;
    }
}
