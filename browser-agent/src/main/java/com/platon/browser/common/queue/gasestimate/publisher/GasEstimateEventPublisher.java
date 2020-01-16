package com.platon.browser.common.queue.gasestimate.publisher;

import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.EventTranslatorVararg;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.util.DaemonThreadFactory;
import com.platon.browser.common.queue.AbstractPublisher;
import com.platon.browser.common.queue.gasestimate.event.GasEstimateEvent;
import com.platon.browser.common.queue.gasestimate.handler.IGasEstimateEventHandler;
import com.platon.browser.dao.entity.GasEstimate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Gas price 估算事件发布者
 */
@Slf4j
@Component
public class GasEstimateEventPublisher extends AbstractPublisher<GasEstimateEvent> {
    private static final EventTranslatorVararg<GasEstimateEvent>
    TRANSLATOR = (event, sequence,args)->{
        event.setSeq((Long) args[0]);
        event.setEstimateList((List<GasEstimate>) args[1]);
    };
    @Value("${disruptor.queue.gasestimate.buffer-size}")
    private int ringBufferSize;
    @Override
    public int getRingBufferSize() {
        return ringBufferSize;
    }
    private EventFactory<GasEstimateEvent> eventFactory = GasEstimateEvent::new;
    @Autowired
    private IGasEstimateEventHandler handler;

    @PostConstruct
    private void init(){
        Disruptor<GasEstimateEvent> disruptor = new Disruptor<>(eventFactory, ringBufferSize, DaemonThreadFactory.INSTANCE);
        disruptor.handleEventsWith(handler);
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
        register(GasEstimateEventPublisher.class.getSimpleName(),this);
    }

    public void publish(Long seq,List<GasEstimate> estimateList) {
        ringBuffer.publishEvent(TRANSLATOR, seq,estimateList);
    }
}
