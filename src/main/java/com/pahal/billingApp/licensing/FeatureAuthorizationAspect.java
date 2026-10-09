package com.pahal.billingApp.licensing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
// Annotation binding needs ExposeInvocationInterceptor (HIGHEST_PRECEDENCE + 1)
// to run first. Authorization still precedes the default transaction advice.
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FeatureAuthorizationAspect {
    private final ModuleAccessService access;
    public FeatureAuthorizationAspect(ModuleAccessService access) { this.access = access; }
    @Before("@annotation(requirement)")
    public void check(RequiresFeature requirement) { access.require(requirement.value()); }
}
