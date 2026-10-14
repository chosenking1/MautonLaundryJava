package com.work.mautonlaundry.dtos;

import com.work.mautonlaundry.dtos.requests.addressrequests.CreateAddressRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dispatch is entirely geographic -- laundromats are ranked by distance from the
 * pickup point and riders are found by widening radius rings -- so an address
 * without coordinates can never be dispatched. These pin the rule that such an
 * address cannot be created in the first place.
 */
class CreateAddressRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CreateAddressRequest valid() {
        CreateAddressRequest r = new CreateAddressRequest();
        r.setStreet("12 Ikorodu Road");
        r.setCity("Lagos");
        r.setLatitude(6.5244);
        r.setLongitude(3.3792);
        return r;
    }

    private Set<String> messages(CreateAddressRequest r) {
        return validator.validate(r).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void addressWithCoordinatesIsAccepted() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void missingCoordinatesAreRejectedWithMapGuidance() {
        CreateAddressRequest r = valid();
        r.setLatitude(null);
        r.setLongitude(null);
        assertThat(messages(r)).containsExactly("Drop a pin on the map so we can find your address");
    }

    @Test
    void missingLatitudeAloneIsRejected() {
        CreateAddressRequest r = valid();
        r.setLatitude(null);
        assertThat(messages(r)).isNotEmpty();
    }

    @Test
    void outOfRangeCoordinatesAreRejected() {
        // A longitude sent in the latitude field is the classic transposition,
        // and lands the customer somewhere dispatch cannot serve.
        CreateAddressRequest r = valid();
        r.setLatitude(181.0);
        assertThat(messages(r)).contains("That location is not valid");

        CreateAddressRequest r2 = valid();
        r2.setLongitude(-200.0);
        assertThat(messages(r2)).contains("That location is not valid");
    }

    @Test
    void streetAndCityRemainRequired() {
        CreateAddressRequest r = valid();
        r.setStreet("  ");
        r.setCity("");
        assertThat(messages(r)).contains("Street is required", "City is required");
    }
}
