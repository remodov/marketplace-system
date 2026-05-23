package ru.vikulinva.customer.adapter.in.rest.mapper;

import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import ru.vikulinva.customer.core.customer.usecase.dto.CustomerView;
import ru.vikulinva.customer.generated.api.model.CustomerResponse;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-05-23T14:31:41+0300",
    comments = "version: 1.6.3, compiler: IncrementalProcessingEnvironment from gradle-language-java-9.0.0.jar, environment: Java 21.0.11 (Microsoft)"
)
@Component
public class CustomerResponseMapperImpl implements CustomerResponseMapper {

    @Override
    public CustomerResponse toRest(CustomerView view) {
        if ( view == null ) {
            return null;
        }

        CustomerResponse customerResponse = new CustomerResponse();

        customerResponse.setId( view.id() );
        customerResponse.setEmail( view.email() );
        customerResponse.setFirstName( view.firstName() );
        customerResponse.setLastName( view.lastName() );
        customerResponse.setPhone( view.phone() );
        customerResponse.setStatus( mapStatus( view.status() ) );
        customerResponse.setCreatedAt( mapInstant( view.createdAt() ) );
        customerResponse.setUpdatedAt( mapInstant( view.updatedAt() ) );

        return customerResponse;
    }
}
