package ru.vikulinva.customer.adapter.in.rest.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import ru.vikulinva.customer.core.customer.usecase.registercustomer.RegisterCustomerUseCase;
import ru.vikulinva.customer.core.customer.usecase.updateprofile.UpdateProfileUseCase;
import ru.vikulinva.customer.generated.api.model.RegisterCustomerRequest;
import ru.vikulinva.customer.generated.api.model.UpdateProfileRequest;

import java.util.UUID;

@Mapper(componentModel = "spring")
public interface CustomerRequestMapper {

    @Mapping(target = "email", source = "email")
    @Mapping(target = "firstName", source = "firstName")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "phone", source = "phone")
    RegisterCustomerUseCase toRegisterCustomer(RegisterCustomerRequest request);

    @Mapping(target = "customerId", source = "customerId")
    @Mapping(target = "firstName", source = "request.firstName")
    @Mapping(target = "lastName", source = "request.lastName")
    @Mapping(target = "phone", source = "request.phone")
    UpdateProfileUseCase toUpdateProfile(UUID customerId, UpdateProfileRequest request);
}
