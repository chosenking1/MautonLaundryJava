package com.work.mautonlaundry.exceptions.deliverymanagementexception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class DeliveryRecordNotFoundException {
}
