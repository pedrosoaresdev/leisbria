package br.com.pandecta.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Injeta o {@link AuthUser} no controller. Se {@code required} e não houver login, responde 401. */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
    boolean required() default true;
}
