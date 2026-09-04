package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.Customer;
import ai.revenue.recovery.entity.Requests.CustomerLoginRequest;
import ai.revenue.recovery.entity.Requests.CustomerSignUpRequest;
import ai.revenue.recovery.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository){
        this.customerRepository = customerRepository;
    }

    public Customer createCustomer(CustomerSignUpRequest request) {

        Customer customer = Customer.builder()
                .phoneNumber(request.getPhoneNumber())
                .email(request.getEmail())
                .password(request.getPassword())
                .name(request.getName())
                .createdAt(LocalDateTime.now())
                .recovered(new BigDecimal(0))
                .build();

        customer = customerRepository.save(customer);
        return customer;
    }

    public Customer getCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Customer not found with ID: " + id));
    }

    public Customer findByEmailAndPassword(CustomerLoginRequest request){
        return customerRepository.findByEmailAndPassword(request.getEmail(), request.getPassword());
    }

    public Customer findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }
}
