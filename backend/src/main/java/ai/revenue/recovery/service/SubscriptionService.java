package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.Requests.SubscriptionRequest;
import ai.revenue.recovery.entity.Responses.SubscriptionResponse;
import ai.revenue.recovery.entity.Subscription;
import ai.revenue.recovery.entity.enums.SubscriptionStatus;
import ai.revenue.recovery.repository.SubscriptionRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final CustomerService customerService;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, CustomerService customerService){
        this.subscriptionRepository = subscriptionRepository;
        this.customerService = customerService;
    }

    public Subscription createSubscription(SubscriptionRequest request) {
        Customer customer = customerService.getCustomer(request.getCustomerId());
        Subscription subscription = new Subscription();
        subscription.setCustomer(customer);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setPlanAmount(request.getAmount());
        subscription.setDescription(request.getDescription());
        LocalDateTime nextPaymentDateTime = request.getPaymentDateTime().plusSeconds(request.getTimeSpan());
        subscription.setNextChargeDate(nextPaymentDateTime);

        return subscriptionRepository.save(subscription);
    }

    public List<Subscription> getAllSubscriptions() {
        return subscriptionRepository.findAll();
    }
}
