package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.PaymentAttempt;
import ai.revenue.recovery.entity.Requests.SubscriptionRequest;
import ai.revenue.recovery.entity.Responses.SubscriptionResponse;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.service.SubscriptionService;
import ai.revenue.recovery.service.RazorpayIntegrationService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/api")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final RazorpayIntegrationService razorpayService;

    public SubscriptionController(SubscriptionService subscriptionService,
                                  RazorpayIntegrationService razorpayService){
        this.subscriptionService = subscriptionService;
        this.razorpayService = razorpayService;
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

    @GetMapping("/subscriptions/customer/{customerId}")
    public ResponseEntity<List<Subscription>> getSubscriptionsByCustomer(@PathVariable Long customerId){
        return ResponseEntity.ok(subscriptionService.getSubscriptionsByCustomerId(customerId));
    }

    @PostMapping("/subscriptions/{id}/initiate-payment")
    public ResponseEntity<?> initiatePayment(@PathVariable Long id) {
        Subscription subscription = subscriptionService.getSubscriptionById(id);
        
        try {
            String receipt = "sub_rcpt_" + subscription.getId() + "_" + System.currentTimeMillis();
            String orderId = razorpayService.createOrder(subscription.getPlanAmount(), receipt, Map.of(
                "subscription_id", String.valueOf(subscription.getId())
            ));
            return ResponseEntity.ok(Map.of(
                "razorpayOrderId", orderId,
                "amount", subscription.getPlanAmount()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to create Razorpay order: " + e.getMessage());
        }
    }


}
