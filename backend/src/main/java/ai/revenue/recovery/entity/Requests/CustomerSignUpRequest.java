package ai.revenue.recovery.entity.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;


@Getter
public class CustomerSignUpRequest {

    @NotNull
    private String name;
    @NotNull
    private String phoneNumber;
    @NotNull
    private String email;
    @NotNull
    private String password;

}
