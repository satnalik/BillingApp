package com.pahal.billingApp.licensing;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface RequiresFeature { Feature value(); }
