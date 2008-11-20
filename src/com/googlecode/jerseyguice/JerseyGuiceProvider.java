package com.googlecode.jerseyguice;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.model.AbstractResource;
import com.sun.jersey.spi.resource.ResourceProvider;
import com.sun.jersey.spi.service.ComponentProvider;

/**
 * @author Sundaramurthi Saminathan
 *
 * @param <T>
 */
public class JerseyGuiceProvider<T> implements ResourceProvider {

	private Injector 			guiceInjector;
	private Class<?> 			resourceClass;
	
	public Object getInstance(ComponentProvider componentProvider, HttpContext context) {
		try {
			return guiceInjector.getInstance(resourceClass);
		} catch (Exception e) {
			throw new ContainerException("Unable to create resource", e);
		} 
	}

	public void init(ComponentProvider arg0, ComponentProvider arg1,
			AbstractResource abstractResource) {
		try {
			Class<? extends Module> moduleClass = abstractResource
			.getResourceClass().getAnnotation(JerseyGuiceManaged.class).module();
			guiceInjector = Guice.createInjector(moduleClass.newInstance());
			
			this.resourceClass = abstractResource.getResourceClass();
		} catch (InstantiationException e) {
			throw new Error("Failed to init resource "+e);
		} catch (IllegalAccessException e) {
			throw new Error("Failed to init resource "+e);
		}
	}
}
