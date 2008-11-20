package com.googlecode.jerseyguice.example;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class GuiceInjector extends AbstractModule {
    protected void configure() {
    	bind(IService.class).to(ServiceImpl.class).in(
				Scopes.SINGLETON);
    }
}