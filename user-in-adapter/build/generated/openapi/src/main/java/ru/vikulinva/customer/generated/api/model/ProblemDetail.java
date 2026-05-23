package ru.vikulinva.customer.generated.api.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.net.URI;
import java.time.OffsetDateTime;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;


import java.util.*;
import jakarta.annotation.Generated;

/**
 * ProblemDetail
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2026-05-23T14:31:18.204269+03:00[Europe/Moscow]", comments = "Generator version: 7.10.0")
public class ProblemDetail {

  private URI type;

  private String title;

  private Integer status;

  private String detail;

  private URI instance;

  private String code;

  public ProblemDetail type(URI type) {
    this.type = type;
    return this;
  }

  /**
   * Get type
   * @return type
   */
  @Valid 
  @JsonProperty("type")
  public URI getType() {
    return type;
  }

  public void setType(URI type) {
    this.type = type;
  }

  public ProblemDetail title(String title) {
    this.title = title;
    return this;
  }

  /**
   * Get title
   * @return title
   */
  
  @JsonProperty("title")
  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public ProblemDetail status(Integer status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  
  @JsonProperty("status")
  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public ProblemDetail detail(String detail) {
    this.detail = detail;
    return this;
  }

  /**
   * Get detail
   * @return detail
   */
  
  @JsonProperty("detail")
  public String getDetail() {
    return detail;
  }

  public void setDetail(String detail) {
    this.detail = detail;
  }

  public ProblemDetail instance(URI instance) {
    this.instance = instance;
    return this;
  }

  /**
   * Get instance
   * @return instance
   */
  @Valid 
  @JsonProperty("instance")
  public URI getInstance() {
    return instance;
  }

  public void setInstance(URI instance) {
    this.instance = instance;
  }

  public ProblemDetail code(String code) {
    this.code = code;
    return this;
  }

  /**
   * Get code
   * @return code
   */
  
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProblemDetail problemDetail = (ProblemDetail) o;
    return Objects.equals(this.type, problemDetail.type) &&
        Objects.equals(this.title, problemDetail.title) &&
        Objects.equals(this.status, problemDetail.status) &&
        Objects.equals(this.detail, problemDetail.detail) &&
        Objects.equals(this.instance, problemDetail.instance) &&
        Objects.equals(this.code, problemDetail.code);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, title, status, detail, instance, code);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ProblemDetail {\n");
    sb.append("    type: ").append(toIndentedString(type)).append("\n");
    sb.append("    title: ").append(toIndentedString(title)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    detail: ").append(toIndentedString(detail)).append("\n");
    sb.append("    instance: ").append(toIndentedString(instance)).append("\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
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

