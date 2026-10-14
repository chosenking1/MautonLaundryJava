package com.work.mautonlaundry.dtos.requests.addressrequests;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAddressRequest {

    @NotBlank(message = "Street is required")
    private String street;

    private Integer streetNumber;

    @NotBlank(message = "City is required")
    private String city;

    private String state;
    private String zip;
    private String country;

    // Coordinates are mandatory, not decoration. Dispatch is entirely
    // geographic: LaundryAssignmentService ranks laundromats by distance from
    // the pickup point, and DispatchEngine widens through the configured radius
    // rings looking for a rider. An address without a pin cannot be dispatched
    // at all -- the booking screen already refuses to let one be selected -- so
    // rejecting it at creation is kinder than storing an address that silently
    // cannot be used.
    //
    // The range bounds catch transposed or garbage values (a longitude sent as
    // a latitude, a zero from an uninitialised field) which would otherwise
    // place the customer in the Gulf of Guinea and quietly break dispatch.
    @NotNull(message = "Drop a pin on the map so we can find your address")
    @DecimalMin(value = "-90.0", message = "That location is not valid")
    @DecimalMax(value = "90.0", message = "That location is not valid")
    private Double latitude;

    @NotNull(message = "Drop a pin on the map so we can find your address")
    @DecimalMin(value = "-180.0", message = "That location is not valid")
    @DecimalMax(value = "180.0", message = "That location is not valid")
    private Double longitude;
}
