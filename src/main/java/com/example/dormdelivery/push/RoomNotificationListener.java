package com.example.dormdelivery.push;

import com.example.dormdelivery.room.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.*;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.*;
import java.nio.charset.StandardCharsets;

@Component
public class RoomNotificationListener {
    private final RoomMemberRepository members;
    private final RoomRepository rooms;
    private final PushSubscriptionRepository subscriptions;
    private final WebPushSender sender;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(100),r -> {
        Thread thread=new Thread(r,"web-push"); thread.setDaemon(true); return thread;
    },new ThreadPoolExecutor.AbortPolicy());
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(RoomNotificationListener.class);
    public RoomNotificationListener(RoomMemberRepository members,RoomRepository rooms,PushSubscriptionRepository subscriptions,WebPushSender sender) {
        this.members=members; this.rooms=rooms; this.subscriptions=subscriptions; this.sender=sender;
    }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void onNotice(RoomNotice notice) {
        if(!sender.available()) return;
        try { executor.execute(() -> deliver(notice)); }
        catch(RejectedExecutionException e) { log.warn("푸시 대기열이 가득 차 알림을 생략했습니다."); }
    }
    public void deliver(RoomNotice notice) {
        try {
            if(!rooms.existsById(notice.roomId())) return;
            var recipients=notice.recipients(members.findByRoomId(notice.roomId()));
            if(recipients.isEmpty()) return;
            String payload="{\"title\":\"단짝\",\"body\":\""+notice.body()+"\",\"url\":\"/?room="+notice.roomId()+"\",\"tag\":\"room-"+notice.roomId()+"-"+notice.type()+"\"}";
            for(var subscription:subscriptions.findByUserIdIn(recipients)) {
                try {
                    if(!members.existsByRoom_IdAndUser_Id(notice.roomId(),subscription.getUserId())) continue;
                    if(!subscriptions.existsById(subscription.getId())) continue;
                    int status=sender.send(subscription,payload.getBytes(StandardCharsets.UTF_8));
                    if(status==404 || status==410) subscriptions.deleteById(subscription.getId());
                    else if(status>=300) log.warn("푸시 서비스 응답 오류: HTTP {}",status);
                } catch(Exception e) { log.warn("개별 푸시 발송 실패; 기존 작업은 유지됩니다."); }
            }
        } catch(Exception e) { log.warn("푸시 수신 대상 조회 실패; 기존 작업은 유지됩니다."); }
    }
    @PreDestroy public void close() { executor.shutdownNow(); }
}
