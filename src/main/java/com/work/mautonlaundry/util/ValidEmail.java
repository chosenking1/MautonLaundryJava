package com.work.mautonlaundry.util;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = EmailValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmail {
    String message() default "Please provide a valid email address";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}