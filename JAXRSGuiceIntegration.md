# How to Install: #

Download the jerseyguice-XX.jar from download page and put it on the classpath of your web services project. You also have to download the guice libraries.

## Step-by-step: ##

Download the jar (jerseyguice-XX.jar) Put it on the classpath

Annotate your web service with @JerseyGuiceManaged(module = YourModule.class) (check below for example)

Inject dependecies like crazy


---


Example
The only thing you need to change in your current JAX RS provider is to annotate them with @JerseyGuiceManaged like below. For guice details, please check the guice documentation
```
@Path("/test")
@JerseyGuiceManaged(module=GuiceInjector.class)
public class ExampleService {
    @Inject
    Service myService;
    ....
    ...
}

```
The GuiceInjector looks like this:

```
public class GuiceInjector extends AbstractModule {
    protected void configure() {
        bind(Service.class).to(ServiceImpl.class).in(Singleton.class);
    }
}

```