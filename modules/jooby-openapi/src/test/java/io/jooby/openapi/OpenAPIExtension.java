package io.jooby.openapi;

import io.jooby.internal.openapi.OpenAPIExt;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public class OpenAPIExtension implements ParameterResolver, AfterEachCallback {

  @Override public boolean supportsParameter(ParameterContext parameterContext,
      ExtensionContext extensionContext) throws ParameterResolutionException {
    Parameter parameter = parameterContext.getParameter();
    return parameter.getType() == RouteIterator.class || parameter.getType() == OpenAPIResult.class;
  }

  @Override public Object resolveParameter(ParameterContext parameterContext,
      ExtensionContext context) throws ParameterResolutionException {
    AnnotatedElement method = context.getElement()
        .orElseThrow(() -> new IllegalStateException("Context: " + context));
    OpenAPITest metadata = method.getAnnotation(OpenAPITest.class);
    Class klass = metadata.value();
    String classname = klass.getName();
    Set<DebugOption> debugOptions = metadata.debug().length == 0
        ? Collections.emptySet()
        : EnumSet.copyOf(Arrays.asList(metadata.debug()));

    OpenAPIGenerator tool = newTool(debugOptions, klass);
    String templateName = classname.replace(".", "/").toLowerCase() + ".yaml";
    tool.setTemplateName(templateName);
    if (metadata.includes().length() > 0) {
      tool.setIncludes(metadata.includes());
    }
    if (metadata.excludes().length() > 0) {
      tool.setExcludes(metadata.excludes());
    }
    Parameter parameter = parameterContext.getParameter();
    OpenAPIExt openAPI = (OpenAPIExt) tool.generate(classname);
    OpenAPIResult result = new OpenAPIResult(openAPI);
    if (parameter.getType() == OpenAPIResult.class) {
      return result;
    }
    RouteIterator iterator = result.iterator(metadata.ignoreArguments());
    getStore(context).put("iterator", iterator);
    return iterator;
  }

  @Override public void afterEach(ExtensionContext ctx) {
    RouteIterator iterator = (RouteIterator) getStore(ctx).get("iterator");
    if (iterator != null) {
      iterator.verify();
    }
  }

  private OpenAPIGenerator newTool(Set<DebugOption> debug, Class klass) {
    String metaInf = Optional.ofNullable( klass.getPackage())
        .map(Package::getName)
        .map(name  -> name.replace(".", "/") + "/")
        .orElse("")
        + klass.getSimpleName();

    OpenAPIGenerator tool = new OpenAPIGenerator(metaInf);
    tool.setDebug(debug);
    return tool;
  }

  private ExtensionContext.Store getStore(ExtensionContext context) {
    Optional<Method> testMethod = context.getTestMethod();
    return context.getStore(
        ExtensionContext.Namespace.create(context.getRequiredTestClass(), testMethod.get()));
  }
}
