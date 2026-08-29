package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Requests.SubscriptionRequest;
import ai.revenue.recovery.entity.Responses.SubscriptionResponse;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.service.SubscriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    public SubscriptionController(SubscriptionService subscriptionService){
        this.subscriptionService = subscriptionService;
    }

    @PostMapping("/create-subscription")
    public ResponseEntity<Subscription> createSubscription(@RequestBody SubscriptionRequest request){
        return ResponseEntity.ok(subscriptionService.createSubscription(request));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<List<Subscription>> getAllSubscription(){
        return ResponseEntity.ok(subscriptionService.getAllSubscriptions());
    }

    @GetMapping("/subscriptions/transactions")
    public ResponseEntity<List<PaymentAttempt>> getAllSubscriptionTransactions(){
        return ResponseEntity.ok(subscriptionService.getAllSubscriptionTransactions());
    }
}
