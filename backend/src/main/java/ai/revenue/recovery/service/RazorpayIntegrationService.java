package ai.revenue.recovery.service;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class RazorpayIntegrationService {

    private final RazorpayClient razorpayClient;

    public RazorpayIntegrationService(
            @Value("${razorpay.key.id}") String keyId,
            @Value("${razorpay.key.secret}") String keySecret) throws RazorpayException {
        this.razorpayClient = new RazorpayClient(keyId, keySecret);
    }

    public String createOrder(BigDecimal amount, String receiptId) {
        try {
            JSONObject orderRequest = new JSONObject();
            // amount in paise
            orderRequest.put("amount", amount.multiply(new BigDecimal(100)).intValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", receiptId);
            Order order = razorpayClient.orders.create(orderRequest);
            return order.get("id");
        } catch (RazorpayException e) {
            throw new RuntimeException("Failed to create Razorpay order", e);
        }
    }

    public Payment fetchPaymentStatus(String razorpayOrderId) {
        try {
            // For demo purposes, we fetch the payments for the order and return the first one.
            // If it fails because credentials are bad, it will throw RazorpayException.
            var payments = razorpayClient.orders.fetchPayments(razorpayOrderId);
            if (payments != null && !payments.isEmpty()) {
                return payments.get(0);
            }
            return null;
        } catch (RazorpayException e) {
            throw new RuntimeException("Failed to fetch payments for order", e);
        }
    }
}
