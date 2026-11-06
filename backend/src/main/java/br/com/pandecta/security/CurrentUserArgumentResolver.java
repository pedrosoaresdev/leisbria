package br.com.pandecta.security;

import br.com.pandecta.common.ApiException;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && AuthUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mav,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Object user = webRequest.getAttribute(AuthUser.REQUEST_ATTR, RequestAttributes.SCOPE_REQUEST);
        CurrentUser ann = parameter.getParameterAnnotation(CurrentUser.class);
        if (user == null && ann != null && ann.required()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Faça login para acessar este recurso.");
        }
        return user;
    }
}
