package ru.vikulinva.customer.generated.api.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import ru.vikulinva.customer.generated.api.model.CustomerStatus;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * CustomerResponse
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-05-23T14:31:18.204269+03:00[Europe/Moscow]", comments = "Generator version: 7.10.0")
public class CustomerResponse {

  private UUID id;

  private String email;

  private String firstName;

  private String lastName;

  private String phone;

  private CustomerStatus status;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime updatedAt;

  public CustomerResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CustomerResponse(UUID id, String email, String firstName, String lastName, CustomerStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    this.id = id;
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public CustomerResponse id(UUID id) {
    this.id = id;
    return this;
  }

  /**
   * Get id
   * @return id
   */
  @NotNull @Valid 
  @JsonProperty("id")
  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public CustomerResponse email(String email) {
    this.email = email;
    return this;
  }

  /**
   * Get email
   * @return email
   */
  @NotNull @jakarta.validation.constraints.Email 
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public CustomerResponse firstName(String firstName) {
    this.firstName = firstName;
    return this;
  }

  /**
   * Get firstName
   * @return firstName
   */
  @NotNull 
  @JsonProperty("firstName")
  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public CustomerResponse lastName(String lastName) {
    this.lastName = lastName;
    return this;
  }

  /**
   * Get lastName
   * @return lastName
   */
  @NotNull 
  @JsonProperty("lastName")
  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public CustomerResponse phone(String phone) {
    this.phone = phone;
    return this;
  }

  /**
   * Get phone
   * @return phone
   */
  
  @JsonProperty("phone")
  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public CustomerResponse status(CustomerStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull @Valid 
  @JsonProperty("status")
  public CustomerStatus getStatus() {
    return status;
  }

  public void setStatus(CustomerStatus status) {
    this.status = status;
  }

  public CustomerResponse createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @NotNull @Valid 
  @JsonProperty("createdAt")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public CustomerResponse updatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /**
   * Get updatedAt
   * @return updatedAt
   */
  @NotNull @Valid 
  @JsonProperty("updatedAt")
  public OffsetDateTime getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(OffsetDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CustomerResponse customerResponse = (CustomerResponse) o;
    return Objects.equals(this.id, customerResponse.id) &&
        Objects.equals(this.email, customerResponse.email) &&
        Objects.equals(this.firstName, customerResponse.firstName) &&
        Objects.equals(this.lastName, customerResponse.lastName) &&
        Objects.equals(this.phone, customerResponse.phone) &&
        Objects.equals(this.status, customerResponse.status) &&
        Objects.equals(this.createdAt, customerResponse.createdAt) &&
        Objects.equals(this.updatedAt, customerResponse.updatedAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, email, firstName, lastName, phone, status, createdAt, updatedAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CustomerResponse {\n");
    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    firstName: ").append(toIndentedString(firstName)).append("\n");
    sb.append("    lastName: ").append(toIndentedString(lastName)).append("\n");
    sb.append("    phone: ").append(toIndentedString(phone)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
    sb.append("    updatedAt: ").append(toIndentedString(updatedAt)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

