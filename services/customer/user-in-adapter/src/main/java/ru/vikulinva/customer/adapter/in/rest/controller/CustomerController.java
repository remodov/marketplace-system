package ru.vikulinva.customer.adapter.in.rest.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;
import ru.vikulinva.customer.adapter.in.rest.mapper.CustomerRequestMapper;
import ru.vikulinva.customer.adapter.in.rest.mapper.CustomerResponseMapper;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.core.customer.usecase.getcustomer.GetCustomerUseCase;
import ru.vikulinva.customer.core.customer.usecase.verifyemail.VerifyEmailUseCase;
import ru.vikulinva.customer.generated.api.CustomerApi;
import ru.vikulinva.customer.generated.api.model.CustomerResponse;
import ru.vikulinva.customer.generated.api.model.RegisterCustomerRequest;
import ru.vikulinva.customer.generated.api.model.UpdateProfileRequest;
import ru.vikulinva.usecase.UseCaseDispatcher;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class CustomerController implements CustomerApi {

    private final UseCaseDispatcher dispatcher;
    private final CustomerRequestMapper requestMapper;
    private final CustomerResponseMapper responseMapper;

    @Override
    public ResponseEntity<CustomerResponse> registerCustomer(RegisterCustomerRequest request) {
        CustomerView view = dispatcher.dispatch(requestMapper.toRegisterCustomer(request));
        CustomerResponse response = responseMapper.toRest(view);
        return ResponseEntity
                .created(URI.create("/v1/customers/" + response.getId()))
                .body(response);
    }

    @Override
    public ResponseEntity<CustomerResponse> verifyEmail(String token) {
        CustomerView view = dispatcher.dispatch(new VerifyEmailUseCase(token));
        return ResponseEntity.ok(responseMapper.toRest(view));
    }

    @Override
    @PreAuthorize("@customerAccess.isSelf(#customerId, authentication)")
    public ResponseEntity<CustomerResponse> getCustomer(UUID customerId) {
        CustomerView view = dispatcher.dispatch(new GetCustomerUseCase(customerId));
        return ResponseEntity.ok(responseMapper.toRest(view));
    }

    @Override
    @PreAuthorize("@customerAccess.isSelf(#customerId, authentication)")
    public ResponseEntity<CustomerResponse> updateProfile(UUID customerId, UpdateProfileRequest request) {
        CustomerView view = dispatcher.dispatch(requestMapper.toUpdateProfile(customerId, request));
        return ResponseEntity.ok(responseMapper.toRest(view));
    }
}
