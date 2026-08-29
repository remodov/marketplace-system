package ru.vikulinva.customer.adapter.in.rest.mapper;

import java.util.UUID;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import ru.vikulinva.customer.core.customer.usecase.registercustomer.RegisterCustomerUseCase;
import ru.vikulinva.customer.core.customer.usecase.updateprofile.UpdateProfileUseCase;
import ru.vikulinva.customer.generated.api.model.RegisterCustomerRequest;
import ru.vikulinva.customer.generated.api.model.UpdateProfileRequest;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-23T14:31:41+0300",
    comments = "version: 1.6.3, compiler: IncrementalProcessingEnvironment from gradle-language-java-9.0.0.jar, environment: Java 21.0.11 (Microsoft)"
)
@Component
public class CustomerRequestMapperImpl implements CustomerRequestMapper {

    @Override
    public RegisterCustomerUseCase toRegisterCustomer(RegisterCustomerRequest request) {
        if ( request == null ) {
            return null;
        }

        String email = null;
        String firstName = null;
        String lastName = null;
        String phone = null;

        email = request.getEmail();
        firstName = request.getFirstName();
        lastName = request.getLastName();
        phone = request.getPhone();

        RegisterCustomerUseCase registerCustomerUseCase = new RegisterCustomerUseCase( email, firstName, lastName, phone );

        return registerCustomerUseCase;
    }

    @Override
    public UpdateProfileUseCase toUpdateProfile(UUID customerId, UpdateProfileRequest request) {
        if ( customerId == null && request == null ) {
            return null;
        }

        String firstName = null;
        String lastName = null;
        String phone = null;
        if ( request != null ) {
            firstName = request.getFirstName();
            lastName = request.getLastName();
            phone = request.getPhone();
        }
        UUID customerId1 = null;
        customerId1 = customerId;

        UpdateProfileUseCase updateProfileUseCase = new UpdateProfileUseCase( customerId1, firstName, lastName, phone );

        return updateProfileUseCase;
    }
}
