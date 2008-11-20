package com.googlecode.jerseyguice;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.google.inject.Module;

import com.sun.jersey.spi.resource.ResourceFactory;

/**
*
* @author Sundaramurthi Saminathan
* @version 1.0
*/
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ResourceFactory(JerseyGuiceProvider.class)
public @interface JerseyGuiceManaged {
	 public Class<? extends Module> module();
}