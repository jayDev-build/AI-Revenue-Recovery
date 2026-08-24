package ai.revenue.recovery.entity.Responses;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class CustomerLoginResponse {
    private String email;
    private String name;
    private Long id;
}
