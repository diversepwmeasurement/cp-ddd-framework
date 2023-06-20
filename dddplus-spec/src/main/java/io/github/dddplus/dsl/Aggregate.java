/*
 * Copyright DDDplus Authors.
 *
 * Licensed under the Apache License version 2.0, available at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.github.dddplus.dsl;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 聚合.
 *
 * <p>Applied on package-info.java.</p>
 */
@Target(ElementType.PACKAGE)
@Retention(RetentionPolicy.SOURCE)
public @interface Aggregate {

    /**
     * Name of the aggregate: the business model context.
     */
    String name();

    /**
     * {@link io.github.dddplus.model.IAggregateRoot}.
     */
    Class[] root() default {};
}