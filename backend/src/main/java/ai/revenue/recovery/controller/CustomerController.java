package ai.revenue.recovery.controller;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.Requests.CustomerLoginRequest;
import ai.revenue.recovery.entity.Requests.CustomerSignUpRequest;
import ai.revenue.recovery.entity.Responses.CustomerLoginResponse;
import ai.revenue.recovery.repository.CustomerRepository;
import ai.revenue.recovery.service.CustomerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@RestController
public class CustomerController {
    
    @Autowired
    private final CustomerRepository customerRepository;
    private final CustomerService customerService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CustomerController(CustomerRepository customerRepository, CustomerService customerService){
        this.customerRepository = customerRepository;
        this.customerService = customerService;
    }

    @GetMapping("/customers")
    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    @GetMapping("/customers/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return customerRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/customer/signup")
    public ResponseEntity<Void> signup(@RequestBody CustomerSignUpRequest request) {
        Customer customer = customerService.createCustomer(request);
        customerRepository.save(customer);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/customer/login")
    public ResponseEntity<CustomerLoginResponse> login(@RequestBody CustomerLoginRequest request) throws JsonProcessingException {
        Customer foundCustomer = customerService.findByEmailAndPassword(request);
        if (foundCustomer == null) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        CustomerLoginResponse loginResponse = CustomerLoginResponse.builder()
                .name(foundCustomer.getName())
                .email(foundCustomer.getEmail())
                .id(foundCustomer.getId())
                .build();

        String jsonPayload = objectMapper.writeValueAsString(loginResponse);

        String encodedPayload = Base64.getEncoder().encodeToString(jsonPayload.getBytes());

        ResponseCookie cookie = ResponseCookie.from("app_payload", encodedPayload)
                .httpOnly(true)       // Prevents XSS attacks (JS cannot read it)
                .secure(true)         // Ensures cookie is sent only over HTTPS
                .path("/")            // Accessible across the entire domain
                .maxAge(86400)        // Expiration time in seconds (e.g., 1 day)
                .sameSite("Lax")      // Protects against CSRF attacks
                .build();


        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(loginResponse);
    }
}
