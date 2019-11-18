package com.platon.browser.task;

import com.platon.browser.common.queue.AbstractPublisher;
import com.platon.browser.common.utils.AppStatusUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Auther: chendongming@juzix.net
 * @Date: 2019/11/16
 * @Description: 环形缓冲区队列监控任务
 */
@Component
@Slf4j
public class RingBufferMonitorTask {

    @Scheduled(cron = "0/10 * * * * ?")
    public void cron () {
        // 只有程序正常运行才执行任务
        if(AppStatusUtil.isRunning()) start();
    }

    protected void start () {
        Map<String,AbstractPublisher> publisherMap = AbstractPublisher.getPublisherMap();
        log.info("RingBuffer info start -------------------------");
        publisherMap.forEach((name,publisher)->log.info("环形缓冲区({}):{}",name,publisher.info()));
        log.info("RingBuffer info end ---------------------------");
    }
}