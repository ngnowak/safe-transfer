package com.nn.safetransfer.annotation;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(TYPE)
@Retention(RUNTIME)
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
public @interface WebSliceTest {
}
