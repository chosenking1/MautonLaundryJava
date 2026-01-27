package com.work.mautonlaundry.security.util;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@permissionService.hasPermission(authentication.name, #resource, #action)")
public @interface RequiresPermission {
    String resource();
    String action();
}