package ru.vikulinva.customer.generated.api.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets CustomerStatus
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-05-23T14:31:18.204269+03:00[Europe/Moscow]", comments = "Generator version: 7.10.0")
public enum CustomerStatus {
  
  PENDING_VERIFICATION("PENDING_VERIFICATION"),
  
  ACTIVE("ACTIVE");

  private String value;

  CustomerStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static CustomerStatus fromValue(String value) {
    for (CustomerStatus b : CustomerStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

