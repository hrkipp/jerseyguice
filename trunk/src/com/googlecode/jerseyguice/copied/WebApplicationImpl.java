package com.googlecode.jerseyguice.copied;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Providers;

import com.google.inject.Guice;
import com.google.inject.Module;
import com.googlecode.jerseyguice.JerseyGuiceManaged;
import com.sun.jersey.api.NotFoundException;
import com.sun.jersey.api.container.ContainerCheckedException;
import com.sun.jersey.api.container.ContainerException;
import com.sun.jersey.api.core.HttpContext;
import com.sun.jersey.api.core.HttpResponseContext;
import com.sun.jersey.api.core.ResourceConfig;
import com.sun.jersey.api.core.ResourceContext;
import com.sun.jersey.api.model.AbstractResource;
import com.sun.jersey.api.model.ResourceModelIssue;
import com.sun.jersey.api.uri.ExtendedUriInfo;
import com.sun.jersey.api.uri.UriTemplate;
import com.sun.jersey.impl.ImplMessages;
import com.sun.jersey.impl.ThreadLocalHttpContext;
import com.sun.jersey.impl.application.ComponentProviderCache;
import com.sun.jersey.impl.application.ContextResolverFactory;
import com.sun.jersey.impl.application.ExceptionMapperFactory;
import com.sun.jersey.impl.application.InjectableProviderFactory;
import com.sun.jersey.impl.application.MessageBodyFactory;
import com.sun.jersey.impl.application.ResourceMethodDispatcherFactory;
import com.sun.jersey.impl.model.ResourceClass;
import com.sun.jersey.impl.model.RulesMap;
import com.sun.jersey.impl.model.parameter.CookieParamInjectableProvider;
import com.sun.jersey.impl.model.parameter.HeaderParamInjectableProvider;
import com.sun.jersey.impl.model.parameter.HttpContextInjectableProvider;
import com.sun.jersey.impl.model.parameter.MatrixParamInjectableProvider;
import com.sun.jersey.impl.model.parameter.PathParamInjectableProvider;
import com.sun.jersey.impl.model.parameter.QueryParamInjectableProvider;
import com.sun.jersey.impl.modelapi.annotation.IntrospectionModeller;
import com.sun.jersey.impl.modelapi.validation.BasicValidator;
import com.sun.jersey.impl.template.TemplateFactory;
import com.sun.jersey.impl.uri.PathPattern;
import com.sun.jersey.impl.uri.PathTemplate;
import com.sun.jersey.impl.uri.rules.ResourceClassRule;
import com.sun.jersey.impl.uri.rules.ResourceObjectRule;
import com.sun.jersey.impl.uri.rules.RightHandPathRule;
import com.sun.jersey.impl.uri.rules.RootResourceClassesRule;
import com.sun.jersey.impl.wadl.WadlFactory;
import com.sun.jersey.impl.wadl.WadlResource;
import com.sun.jersey.spi.MessageBodyWorkers;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponse;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ContainerResponseWriter;
import com.sun.jersey.spi.container.WebApplication;
import com.sun.jersey.spi.inject.Inject;
import com.sun.jersey.spi.inject.Injectable;
import com.sun.jersey.spi.inject.InjectableProvider;
import com.sun.jersey.spi.inject.InjectableProviderContext;
import com.sun.jersey.spi.inject.SingletonTypeInjectableProvider;
import com.sun.jersey.spi.resource.ResourceProviderFactory;
import com.sun.jersey.spi.service.ComponentContext;
import com.sun.jersey.spi.service.ComponentProvider;
import com.sun.jersey.spi.service.ComponentProvider.Scope;
import com.sun.jersey.spi.template.TemplateContext;
import com.sun.jersey.spi.uri.rules.UriRule;

public class WebApplicationImpl implements WebApplication {

    private static final Logger LOGGER = Logger.getLogger(WebApplicationImpl.class.getName());
    
    private final ConcurrentMap<Class, ResourceClass> metaClassMap =
            new ConcurrentHashMap<Class, ResourceClass>();
    
    private final ResourceProviderFactory resolverFactory;
    
    private final ThreadLocalHttpContext context;
    
    private boolean initiated;
    
    private ResourceConfig resourceConfig;
    
    private RootResourceClassesRule rootsRule;
    
    private InjectableProviderFactory injectableFactory;
    
    private MessageBodyFactory bodyFactory;
    
    private ComponentProvider provider;
    
    private ComponentProvider resourceProvider;
    
    private TemplateContext templateContext;
    
    private ExceptionMapperFactory exceptionFactory;
    
    private ResourceMethodDispatcherFactory dispatcherFactory;
    
    private ResourceContext resourceContext;
        
    private List<ContainerRequestFilter> requestFilters = new LinkedList<ContainerRequestFilter>();
    
    private List<ContainerResponseFilter> responseFilters = new LinkedList<ContainerResponseFilter>();

    private WadlFactory wadlFactory;
    
    public WebApplicationImpl() {
        this.resolverFactory = ResourceProviderFactory.getInstance();

        this.context = new ThreadLocalHttpContext();

        InvocationHandler requestHandler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {                
                return method.invoke(context.getRequest(), args);
            }
        };
        InvocationHandler uriInfoHandler = new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                return method.invoke(context.getUriInfo(), args);
            }
        };
        
        // Create injectable provider factory
        this.injectableFactory = new InjectableProviderFactory(); 
        injectableFactory.add(new ContextInjectableProvider<InjectableProviderContext>(
                InjectableProviderContext.class, injectableFactory));
        
        // Add proxied injectables
        final Map<Type, Object> m = new HashMap<Type, Object>();
        m.put(HttpContext.class, context);
        m.put(HttpHeaders.class, createProxy(HttpHeaders.class, requestHandler));
        m.put(UriInfo.class, createProxy(UriInfo.class, uriInfoHandler));
        m.put(ExtendedUriInfo.class, createProxy(ExtendedUriInfo.class, uriInfoHandler));
        m.put(Request.class, createProxy(Request.class, requestHandler));
        m.put(SecurityContext.class, createProxy(SecurityContext.class, requestHandler));        
        injectableFactory.add(new InjectableProvider<Context, Type>() {
            public Scope getScope() {
                return Scope.Singleton;
            }
            
            public Injectable getInjectable(ComponentContext ic, Context a, Type c) {
                final Object o = m.get(c);
                if (o != null) {
                    return new Injectable() {
                        public Object getValue(HttpContext c) {
                            return o;
                        }
                    };
                } else
                    return null;
            }
        });
    }
 
    @Override
    public WebApplication clone() {
        WebApplicationImpl wa = new WebApplicationImpl();
        if (provider instanceof DefaultComponentProvider) {
            wa.initiate(resourceConfig, null);
        } else {
            AdaptingComponentProvider acp = (AdaptingComponentProvider) provider;
            wa.initiate(resourceConfig, acp.getAdaptedComponentProvider());
        }

        return wa;
    }

    public ResourceClass getResourceClass(Class c) {
        assert c != null;

        // Try the non-blocking read, the most common opertaion
        ResourceClass rc = metaClassMap.get(c);
        if (rc != null) {
            return rc;
        }

        // ResourceClass is not present use a synchronized block
        // to ensure that only one ResourceClass instance is created
        // and put to the map
        synchronized (metaClassMap) {
            // One or more threads may have been blocking on the synchronized
            // block, re-check the map
            rc = metaClassMap.get(c);
            if (rc != null) {
                return rc;
            }

            rc = newResourceClass(getAbstractResource(c));
            metaClassMap.put(c, rc);
        }
        rc.init(getComponentProvider(), 
                getResourceComponentProvider(), 
                resolverFactory);
        return rc;
    }

    private ResourceClass getResourceClass(AbstractResource ar) {
        ResourceClass rc = newResourceClass(ar);
        metaClassMap.put(ar.getResourceClass(), rc);
        rc.init(getComponentProvider(), 
                getResourceComponentProvider(), 
                resolverFactory);
        return rc;
    }

    private ResourceClass newResourceClass(final AbstractResource ar) {
        assert null != ar;
        BasicValidator validator = new BasicValidator();
        validator.validate(ar);
        boolean fatalIssueFound = false;
        for (ResourceModelIssue issue : validator.getIssueList()) {
            if (issue.isFatal()) {
                fatalIssueFound = true;
                if (LOGGER.isLoggable(Level.SEVERE)) {
                    LOGGER.severe(issue.getMessage());
                }
            } else {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning(issue.getMessage());
                }
            }
        } // eof model validation
        if (fatalIssueFound) {
            LOGGER.severe(ImplMessages.FATAL_ISSUES_FOUND_AT_RES_CLASS(ar.getResourceClass().getName()));
            throw new ContainerException(ImplMessages.FATAL_ISSUES_FOUND_AT_RES_CLASS(ar.getResourceClass().getName()));
        }
        return new ResourceClass(
                resourceConfig,
                getComponentProvider(), 
                dispatcherFactory,
                injectableFactory, 
                ar,
                this.wadlFactory);
    }

    private AbstractResource getAbstractResource(Object o) {
        return getAbstractResource(o.getClass());
    }
    
    private AbstractResource getAbstractResource(Class c) {
        return IntrospectionModeller.createResource(c);
    }

    /**
     * Inject resources onto fields of an object.
     * @param o the object
     */
    private void injectResources(Object o) {
        injectableFactory.injectResources(o);
    }

    private final class AdaptingComponentProvider implements ComponentProvider {

        private final ComponentProvider cp;

        AdaptingComponentProvider(ComponentProvider cp) {
            this.cp = cp;
        }

        public ComponentProvider getAdaptedComponentProvider() {
            return cp;
        }

        //
        public <T> T getInstance(Scope scope, Class<T> c)
                throws InstantiationException, IllegalAccessException {
            T o = cp.getInstance(scope, c);
            if (o == null) {
                o = c.newInstance();
                injectResources(o);
            } else {
                injectResources(cp.getInjectableInstance(o));
            }
            return o;
        }

        public <T> T getInstance(Scope scope, Constructor<T> contructor, Object[] parameters)
                throws InstantiationException, IllegalArgumentException,
                IllegalAccessException, InvocationTargetException {
            T o = cp.getInstance(scope, contructor, parameters);
            if (o == null) {
                o = contructor.newInstance(parameters);
                injectResources(o);
            } else {
                injectResources(cp.getInjectableInstance(o));
            }
            return o;
        }

        public <T> T getInstance(ComponentContext cc, Scope scope, Class<T> c) 
                throws InstantiationException, IllegalAccessException {
            T o = cp.getInstance(cc, scope, c);
            if (o == null) {
                o = c.newInstance();
                injectResources(o);
            } else {
                injectResources(cp.getInjectableInstance(o));
            }
            return o;
        }
        
        public <T> T getInjectableInstance(T instance) {
            return cp.getInjectableInstance(instance);
        }

        public void inject(Object instance) {
            cp.inject(instance);
            injectResources(cp.getInjectableInstance(instance));
        }
    }

    private final static class AdaptingResourceComponentProvider implements ComponentProvider {

        private final ComponentProvider cp;

        AdaptingResourceComponentProvider(ComponentProvider cp) {
            this.cp = cp;
        }

        public ComponentProvider getAdaptedComponentProvider() {
            return cp;
        }

        //
        public <T> T getInstance(Scope scope, Class<T> c)
                throws InstantiationException, IllegalAccessException {
            T o = cp.getInstance(scope, c);
            if (o == null)
                o = c.newInstance();
            return o;
        }

        public <T> T getInstance(Scope scope, Constructor<T> contructor, Object[] parameters)
                throws InstantiationException, IllegalArgumentException,
                IllegalAccessException, InvocationTargetException {
            T o = cp.getInstance(scope, contructor, parameters);
            if (o == null) {
                o = contructor.newInstance(parameters);
            }
            return o;
        }

        public <T> T getInstance(ComponentContext cc, Scope scope, Class<T> c) 
                throws InstantiationException, IllegalAccessException {
            T o = cp. getInstance(cc, scope, c);
            if (o == null)
                o = c.newInstance();            
            return o;
        }
        
        public <T> T getInjectableInstance(T instance) {
            return cp.getInjectableInstance(instance);
        }

        public void inject(Object instance) {
            cp.inject(instance);
        }
    }
    
    private final class DefaultComponentProvider implements ComponentProvider {

        public <T> T getInstance(Scope scope, Class<T> c)
                throws InstantiationException, IllegalAccessException {
        	JerseyGuiceManaged anat = c.getAnnotation(JerseyGuiceManaged.class);
        	T o;
        	if(anat != null){
        		Class<? extends Module> moduleClass = anat.module();
        		o = Guice.createInjector(moduleClass.newInstance()).getInstance(c);
        	}else{
	            o =c.newInstance();
	            injectResources(o);
	            
        	}
        	return o;
        }

        public <T> T getInstance(Scope scope, Constructor<T> contructor, Object[] parameters)
                throws InstantiationException, IllegalArgumentException,
                IllegalAccessException, InvocationTargetException {
        	JerseyGuiceManaged anat = contructor.getAnnotation(JerseyGuiceManaged.class);
        	T o;
        	/*if(anat != null){
        		Class<? extends Module> moduleClass = anat.module();
        		o = Guice.createInjector(moduleClass.newInstance()).getInstance(contructor.getClass());
        	}else{*/
        		o = contructor.newInstance(parameters);
        	//}
            injectResources(o);
            return o;
        }

        public <T> T getInstance(ComponentContext cc, Scope scope, Class<T> c) 
                throws InstantiationException, IllegalAccessException {
            return getInstance(scope, c);
        }
        
        public <T> T getInjectableInstance(T instance) {
            return instance;
        }

        public void inject(Object instance) {
            injectResources(instance);
        }
    }

    private final static class DefaultResourceComponentProvider implements ComponentProvider {

        public <T> T getInstance(Scope scope, Class<T> c)
                throws InstantiationException, IllegalAccessException {
            final T o = c.newInstance();
            return o;
        }

        public <T> T getInstance(Scope scope, Constructor<T> contructor, Object[] parameters)
                throws InstantiationException, IllegalArgumentException,
                IllegalAccessException, InvocationTargetException {
            final T o = contructor.newInstance(parameters);
            return o;
        }

        public <T> T getInstance(ComponentContext cc, Scope scope, Class<T> c) 
                throws InstantiationException, IllegalAccessException {
            return getInstance(scope, c);
        }
        
        public <T> T getInjectableInstance(T instance) {
            return instance;
        }

        public void inject(Object instance) {
        }
    }
    
    // Singleton
    
    public void initiate(ResourceConfig resourceConfig) {
        initiate(resourceConfig, null);
    }

    private static class ContextInjectableProvider<T> extends
            SingletonTypeInjectableProvider<Context, T> {
        ContextInjectableProvider(Type type, T instance) {
            super(type, instance);
        }
    }

    public void initiate(ResourceConfig resourceConfig, ComponentProvider _provider) {
        if (resourceConfig == null) {
            throw new IllegalArgumentException("ResourceConfig instance MUST NOT be null");
        }

        if (initiated) {
            throw new ContainerException(ImplMessages.WEB_APP_ALREADY_INITIATED());
        }
        this.initiated = true;
        
        // Validate the resource config
        resourceConfig.validate();

        // Set up the component provider to be
        // used with non-resource class components
        this.provider = (_provider == null)
                ? new DefaultComponentProvider()
                : new AdaptingComponentProvider(_provider);
        
        // Set up the resource component provider to be
        // used with resource class components
        this.resourceProvider = (_provider == null)
                ? new DefaultResourceComponentProvider()
                : new AdaptingResourceComponentProvider(_provider);
        
        // Check the resource configuration
        this.resourceConfig = resourceConfig;

        this.resourceContext = new ResourceContext() {
            public <T> T getResource(Class<T> c) {
                final ResourceClass rc = getResourceClass(c);
                if (rc == null) {
                    LOGGER.severe("No resource class found for class " + c.getName());
                    throw new ContainerException("No resource class found for class " + c.getName());
                }
                final Object instance = rc.provider.getInstance(resourceProvider, context);
                return instance != null ? c.cast(instance) : null;
            }
        };

        ComponentProviderCache cpc = new ComponentProviderCache(
                    this.injectableFactory,
                    this.provider,
                    resourceConfig.getProviderClasses(),
                    resourceConfig.getProviderSingletons());

        // Add injectable provider for @Inject
        injectableFactory.add(
            new InjectableProvider<Inject, Type>() {
                    public Scope getScope() {
                        return Scope.Undefined;
                    }

                    @SuppressWarnings("unchecked")
                    public Injectable<Object> getInjectable(ComponentContext ic, Inject a, final Type c) {
                        if (!(c instanceof Class))
                            return null;
                        
                        final InjectableProviderFactory.AccessibleObjectContext aic = 
                                new InjectableProviderFactory.AccessibleObjectContext(
                                ic.getAccesibleObject(), ic.getAnnotations());
                        return new Injectable<Object>() {
                            public Object getValue(HttpContext context) {
                                try {
                                    return provider.getInstance(aic, Scope.Undefined, (Class)c);
                                } catch (Exception e) {
                                    LOGGER.log(Level.SEVERE, "Could not get instance from component provider for type " +
                                            c, e);
                                    throw new ContainerException("Could not get instance from component provider for type " +
                                            c, e);
                                }
                            }
                        };
                    }

                });
        
        // Allow injection of resource config
        injectableFactory.add(new ContextInjectableProvider<ResourceConfig>(
                ResourceConfig.class, resourceConfig));

        // Allow injection of resource context
        injectableFactory.add(new ContextInjectableProvider<ResourceContext>(
                ResourceContext.class, resourceContext));
        
        injectableFactory.configure(cpc);
        
        // Add per-request-based injectable providers
        injectableFactory.add(new CookieParamInjectableProvider());
        injectableFactory.add(new HeaderParamInjectableProvider());
        injectableFactory.add(new HttpContextInjectableProvider());
        injectableFactory.add(new MatrixParamInjectableProvider());
        injectableFactory.add(new PathParamInjectableProvider());
        injectableFactory.add(new QueryParamInjectableProvider());

        // Obtain all context resolvers
        final ContextResolverFactory crf = new ContextResolverFactory(cpc, 
                injectableFactory);
        
        // Obtain all the templates
        this.templateContext = new TemplateFactory(cpc);
        // Allow injection of template context
        injectableFactory.add(new ContextInjectableProvider<TemplateContext>(
                TemplateContext.class, templateContext));

        // Obtain all the exception mappers
        this.exceptionFactory = new ExceptionMapperFactory(cpc);
        
        // Obtain all resource method dispatchers
        this.dispatcherFactory = new ResourceMethodDispatcherFactory(cpc);
        
        // Obtain all message body readers/writers
        this.bodyFactory = new MessageBodyFactory(cpc);
        injectableFactory.add(
                new ContextInjectableProvider<MessageBodyWorkers>(
                MessageBodyWorkers.class, bodyFactory));
        
        // Injection of Providers
        Providers p = new Providers() {
            public <T> MessageBodyReader<T> getMessageBodyReader(Class<T> c, Type t, 
                    Annotation[] as, MediaType m) {
                return bodyFactory.getMessageBodyReader(c, t, as, m);
            }

            public <T> MessageBodyWriter<T> getMessageBodyWriter(Class<T> c, Type t, 
                    Annotation[] as, MediaType m) {
                return bodyFactory.getMessageBodyWriter(c, t, as, m);
            }

            public <T extends Throwable> ExceptionMapper<T> getExceptionMapper(Class<T> c) {
                if (Throwable.class.isAssignableFrom(c)) 
                   return exceptionFactory.find((Class<Throwable>)c);
                else
                    return null;
            }

            public <T> ContextResolver<T> getContextResolver(Class<T> ct, MediaType m) {
                return crf.resolve(ct, m);
            }
        };
        injectableFactory.add(
                new ContextInjectableProvider<Providers>(
                Providers.class, p));
        
        // Initiate message body readers/writers
        bodyFactory.init();
        
        // Add per-request-based injectable providers
        injectableFactory.add(new CookieParamInjectableProvider());
        injectableFactory.add(new HeaderParamInjectableProvider());
        injectableFactory.add(new HttpContextInjectableProvider());
        injectableFactory.add(new MatrixParamInjectableProvider());
        injectableFactory.add(new PathParamInjectableProvider());
        injectableFactory.add(new QueryParamInjectableProvider());

        // Intiate filters
        FilterFactory ff = new FilterFactory(cpc);
        // Initiate request filters
        requestFilters.addAll(ff.getRequestFilters(
                resourceConfig.getProperty(
                    ResourceConfig.PROPERTY_CONTAINER_REQUEST_FILTERS)));
        requestFilters.addAll(cpc.getServices(ContainerRequestFilter.class));        
        // Initiate response filters
        responseFilters.addAll(ff.getResponseFilters(
                resourceConfig.getProperty(
                    ResourceConfig.PROPERTY_CONTAINER_RESPONSE_FILTERS)));
        responseFilters.addAll(cpc.getServices(ContainerResponseFilter.class));        
        
        // Inject on all components
        cpc.injectOnComponents();
        
        this.wadlFactory = new WadlFactory( resourceConfig );
        
        // Obtain all root resources
        this.rootsRule = new RootResourceClassesRule(processRootResources());
    }

    public MessageBodyWorkers getMessageBodyWorkers() {
        return bodyFactory;
    }

    public ComponentProvider getComponentProvider() {
        return provider;
    }

    public ComponentProvider getResourceComponentProvider() {
        return resourceProvider;
    }
    
    public void handleRequest(ContainerRequest request, ContainerResponseWriter responseWriter) 
            throws IOException {
        final ContainerResponse response = new ContainerResponse(
                this,
                request,
                responseWriter);
        handleRequest(request, response);
    }
    
    public void handleRequest(ContainerRequest request, ContainerResponse response) throws IOException {
        try {
            final WebApplicationContext localContext = new WebApplicationContext(this, request, response);
            context.set(localContext);

            /**
             * The matching algorithm currently works from an absolute path.
             * The path is required to be in encoded form.
             */
            StringBuilder path = new StringBuilder();
            path.append("/").append(localContext.getPath(false));

            if (!resourceConfig.getFeature(ResourceConfig.FEATURE_MATCH_MATRIX_PARAMS)) {
                path = stripMatrixParams(path);
            }

            // TODO convert to filter
            // If there are URI conneg extensions for media and language
            if (!resourceConfig.getMediaTypeMappings().isEmpty() ||
                    !resourceConfig.getLanguageMappings().isEmpty()) {
                uriConneg(path, request);
            }
        
            for (ContainerRequestFilter f : requestFilters)
                request = f.filter(request);
            
            if (!rootsRule.accept(path, null, localContext)) {
                throw new NotFoundException();
            }            
        } catch (WebApplicationException e) {
            mapWebApplicationException(e, response);
        } catch (ContainerCheckedException e) {
            if (!mapException(e.getCause(), response)) {
                context.set(null);
                throw e;
            }
        } catch (RuntimeException e) {
            if (!mapException(e, response)) {
                context.set(null);
                throw e;
            }
        }

        try {
            for (ContainerResponseFilter f : responseFilters)
                response = f.filter(request, response);
        } catch (WebApplicationException e) {
            mapWebApplicationException(e, response);
        } catch (RuntimeException e) {
            if (!mapException(e, response)) {
                context.set(null);
                throw e;
            }
        }

        try {
            response.write();
        } catch (WebApplicationException e) {
            if (response.isCommitted()) {
                throw e;
            } else {
                mapWebApplicationException(e, response);
                response.write();
            }
        } finally {
            context.set(null);
        }
    }

    public HttpContext getThreadLocalHttpContext() {
        return context;
    }

    private RulesMap<UriRule> processRootResources() {
        Set<Class<?>> classes = resourceConfig.getRootResourceClasses();
        Set<Object> singletons = resourceConfig.getRootResourceSingletons();
        if (classes.isEmpty() && singletons.isEmpty()) {
            LOGGER.severe(ImplMessages.NO_ROOT_RES_IN_RES_CFG());
            throw new ContainerException(ImplMessages.NO_ROOT_RES_IN_RES_CFG());
        }

        RulesMap<UriRule> rulesMap = new RulesMap<UriRule>();

        Set<AbstractResource> rootResources = new HashSet<AbstractResource>();
        for (Object o : singletons) {
            AbstractResource ar = getAbstractResource(o);
            if (!ar.isRootResource()) {
                LOGGER.warning("The singleton, " + o + ", registered as a root resource singleton" +
                        "of the ResourceConfig is not a root resource singleton" +
                        ". This singleton will be ignored");
                continue;
            }

            // Inject onto the singleton
            injectResources(o);
            
            ResourceClass r = getResourceClass(ar);
            rootResources.add(r.resource);

            UriTemplate t = new PathTemplate(r.resource.getPath().getValue());
            PathPattern p = new PathPattern(t);

            rulesMap.put(p, new RightHandPathRule(
                    resourceConfig.getFeature(ResourceConfig.FEATURE_REDIRECT),
                    t.endsWithSlash(),
                    new ResourceObjectRule(t, o)));
        }
        
        for (Class<?> c : classes) {
            AbstractResource ar = getAbstractResource(c);
            if (!ar.isRootResource()) {
                LOGGER.warning("The class, " + c + ", registered as a root resource class " +
                        "of the ResourceConfig is not a root resource class" +
                        ". This class will be ignored");
                continue;
            }
            // TODO this should be moved to the validation
            // as such classes are not root resource classes
            int modifiers = c.getModifiers();
            if (Modifier.isAbstract(modifiers) && !Modifier.isInterface(modifiers)) {
                LOGGER.warning("The " + c + ", registered as a root resource class " +
                        "of the ResourceConfig cannot be instantiated" +
                        ". This class will be ignored");
                continue;
            } else if (Modifier.isInterface(modifiers)) {
                LOGGER.warning("The " + c + ", registered as a root resource class " +
                        "of the ResourceConfig cannot be instantiated" +
                        ". This interface will be ignored");
                continue;
            }

            ResourceClass r = getResourceClass(ar);
            rootResources.add(r.resource);

            UriTemplate t = new PathTemplate(r.resource.getPath().getValue());
            PathPattern p = new PathPattern(t);

            rulesMap.put(p, new RightHandPathRule(
                    resourceConfig.getFeature(ResourceConfig.FEATURE_REDIRECT),
                    t.endsWithSlash(),
                    new ResourceClassRule(t, c)));
        }

        createWadlResource(rootResources, rulesMap, wadlFactory);

        return rulesMap;
    }

    private void createWadlResource(Set<AbstractResource> rootResources,
            RulesMap<UriRule> rulesMap,
            WadlFactory wadlFactory) {
        // TODO get ResourceConfig to check the WADL generation feature
        
        
        
        Object wr = wadlFactory.createWadlResource(rootResources);
        if (wr == null) {
            return;
        }

        // Preload wadl resource runtime meta data
        getResourceClass(WadlResource.class);
        UriTemplate t = new PathTemplate("application.wadl");
        PathPattern p = new PathPattern(t);

        rulesMap.put(p, new RightHandPathRule(
                resourceConfig.getFeature(ResourceConfig.FEATURE_REDIRECT),
                false,
                new ResourceObjectRule(t, wr)));
    }

    /**
     * Strip the matrix parameters from a path
     */
    private StringBuilder stripMatrixParams(StringBuilder path) {
        int e = path.indexOf(";");
        if (e == -1) {
            return path;
        }

        int s = 0;
        StringBuilder sb = new StringBuilder();
        do {
            // Append everything up to but not including the ';'
            sb.append(path, s, e);

            // Skip everything up to but not including the '/'
            s = path.indexOf("/", e + 1);
            if (s == -1) {
                break;
            }
            e = path.indexOf(";", s);
        } while (e != -1);

        if (s != -1) {
            // Append any remaining characters
            sb.append(path, s, path.length());
        }

        return sb;
    }

    /**
     * 
     */
    private void uriConneg(StringBuilder path, ContainerRequest request) {
        int si = path.lastIndexOf("/");
        // Path ends in slash
        if (si == path.length() - 1) {
            // Find the next slash
            si = path.lastIndexOf("/", si - 1);
        }
        // If no slash that set to start of path
        if (si == -1) {
            si = 0;
        }

        MediaType accept = null;
        for (Map.Entry<String, MediaType> e : resourceConfig.getMediaTypeMappings().entrySet()) {
            int i = path.indexOf(e.getKey(), si);
            if (i > 0 && (path.charAt(i - 1) == '.')) {
                int lengthWithExt = i + e.getKey().length();
                if (lengthWithExt == path.length()) {
                    accept = e.getValue();
                    path.delete(i - 1, lengthWithExt);
                } else {
                    char charAfterExt = path.charAt(lengthWithExt);
                    if (('/' == charAfterExt) || ('.' == charAfterExt)) { 
                        accept = e.getValue();
                        path.delete(i - 1, lengthWithExt);
                    }
                }
            }
        }
        if (accept != null) {
            // TODO do not modify request headers
            MultivaluedMap<String, String> h = request.getRequestHeaders();
            h.putSingle("Accept", accept.toString());
        }

        String acceptLanguage = null;
        for (Map.Entry<String, String> e : resourceConfig.getLanguageMappings().entrySet()) {
            int i = path.indexOf(e.getKey(), si);
            if (i > 0 && path.charAt(i - 1) == '.') {
                acceptLanguage = e.getValue();
                path.delete(i - 1, i + e.getKey().length());
            }
        }
        if (acceptLanguage != null) {
            // TODO do not modify request headers
            MultivaluedMap<String, String> h = request.getRequestHeaders();
            h.putSingle("Accept-Language", acceptLanguage);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T createProxy(Class<T> c, InvocationHandler i) {
        return (T) Proxy.newProxyInstance(
                this.getClass().getClassLoader(),
                new Class[]{c},
                i);
    }

    private void mapWebApplicationException(WebApplicationException e, 
            HttpResponseContext response) {
        if (e.getResponse().getEntity() != null) {
            onException(e, e.getResponse(), response);
        } else {
            if (!mapException(e, response)) {
                onException(e, e.getResponse(), response);                    
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    private boolean mapException(Throwable e,
            HttpResponseContext response) {
        ExceptionMapper em = exceptionFactory.find(e.getClass());
        if (em == null) return false;

        try {
            Response r = em.toResponse(e);
            if (r == null)
                r = Response.noContent().build();
            onException(e, r, response);
        } catch (RuntimeException ex) {
            LOGGER.severe("Exception mapper " + em +
                    " for throwable " + e +
                    " threw a Runtime exception when " +
                    "attempting to obtain the response");
            Response r = Response.serverError().build();
            onException(ex, r, response);
        }
        return true;
    }
    
    private static void onException(Throwable e,
            Response r,
            HttpResponseContext response) {
        // Log the stack trace
        if (r.getStatus() >= 500) {
            e.printStackTrace();
        }

        if (r.getStatus() >= 500 && r.getEntity() == null) {
            // Write out the exception to a string
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.flush();

            r = Response.status(r.getStatus()).entity(sw.toString()).
                    type("text/plain").build();
        }
        
        response.setResponse(r);        
    }
}
