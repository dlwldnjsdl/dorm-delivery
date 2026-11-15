package com.example.dormdelivery.push;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription,Long> {
    Optional<PushSubscription> findByEndpointHash(String hash);
    List<PushSubscription> findByDeviceHash(String hash);
    List<PushSubscription> findByUserIdIn(Collection<Long> users);
}
