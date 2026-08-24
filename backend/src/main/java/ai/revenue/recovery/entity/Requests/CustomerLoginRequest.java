package ai.revenue.recovery.entity.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
public class CustomerLoginRequest {

    @NotNull
    private String email;
    @NotNull
    private String password;
}
