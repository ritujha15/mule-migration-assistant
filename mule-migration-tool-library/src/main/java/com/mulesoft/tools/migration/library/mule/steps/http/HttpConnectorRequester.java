/*
 * Copyright (c) 2017 MuleSoft, Inc. This software is protected under international
 * copyright law. All use of this software is subject to MuleSoft's Master Subscription
 * Agreement (or other master license agreement) separately entered into in writing between
 * you and MuleSoft. If such an agreement is not in place, you may not use the software.
 */
package com.mulesoft.tools.migration.library.mule.steps.http;

import static com.mulesoft.tools.migration.library.mule.steps.core.properties.InboundPropertiesHelper.addAttributesMapping;
import static com.mulesoft.tools.migration.step.category.MigrationReport.Level.ERROR;
import static com.mulesoft.tools.migration.step.util.XmlDslUtils.getElementsFromDocument;
import static com.mulesoft.tools.migration.step.util.XmlDslUtils.migrateExpression;
import static com.mulesoft.tools.migration.step.util.XmlDslUtils.migrateOperationStructure;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import com.mulesoft.tools.migration.step.category.MigrationReport;

import org.jdom2.Content;
import org.jdom2.Element;
import org.jdom2.Namespace;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migrates the requester operation of the HTTP Connector
 *
 * @author Mulesoft Inc.
 * @since 1.0.0
 */
public class HttpConnectorRequester extends AbstractHttpConnectorMigrationStep {

  public static final String XPATH_SELECTOR = "/mule:mule//http:request";

  @Override
  public String getDescription() {
    return "Update HTTP requester operation.";
  }

  public HttpConnectorRequester() {
    this.setAppliedTo(XPATH_SELECTOR);
  }

  @Override
  public void execute(Element object, MigrationReport report) throws RuntimeException {
    final Namespace httpNamespace = Namespace.getNamespace("http", HTTP_NAMESPACE);
    object.setNamespace(httpNamespace);

    if (object.getAttribute("port") != null) {
      report.report(ERROR, object, object,
                    "'port' cannot be overriden at the HTTP request operation. You may use an expression in the config to make it dynamic.",
                    "https://docs.mulesoft.com/mule-user-guide/v/4.1/migration-connectors-http#http-request");
      object.removeAttribute("port");
    }
    if (object.getAttribute("host") != null) {
      report.report(ERROR, object, object,
                    "'host' cannot be overriden at the HTTP request operation. You may use an expression in the config to make it dynamic.",
                    "https://docs.mulesoft.com/mule-user-guide/v/4.1/migration-connectors-http#http-request");
      object.removeAttribute("host");
    }

    migrateExpression(object.getAttribute("path"), getExpressionMigrator());
    migrateExpression(object.getAttribute("method"), getExpressionMigrator());
    migrateExpression(object.getAttribute("followRedirects"), getExpressionMigrator());
    migrateExpression(object.getAttribute("target"), getExpressionMigrator());

    addAttributesToInboundProperties(object, report);

    object.getChildren().forEach(c -> {
      if (HTTP_NAMESPACE.equals(c.getNamespaceURI())) {
        executeChild(c, report, httpNamespace);
      }
    });

    if (object.getChild("request-builder", httpNamespace) == null) {
      object.addContent(new Element("request-builder", httpNamespace).addContent(compatibilityHeaders(httpNamespace)));
    }

    if (object.getAttribute("source") != null) {
      if (!"#[payload]".equals(object.getAttributeValue("source"))) {
        object.addContent(new Element("body", httpNamespace)
            .setText(getExpressionMigrator().migrateExpression(object.getAttributeValue("source"))));
      }
      object.removeAttribute("source");
    }

    Element requestBuilder = object.getChild("request-builder", httpNamespace);
    if (requestBuilder != null) {
      object.removeContent(requestBuilder);

      Builder<Content> listBuilder = ImmutableList.<Content>builder();

      listBuilder.addAll(requestBuilder.getContent().stream()
          .filter(c -> c instanceof Element
              && ("header".equals(((Element) c).getName()) || "headers".equals(((Element) c).getName())))
          .collect(toList()));
      listBuilder.addAll(requestBuilder.getContent().stream()
          .filter(c -> c instanceof Element
              && ("uri-param".equals(((Element) c).getName()) || "uri-params".equals(((Element) c).getName())))
          .collect(toList()));
      listBuilder.addAll(requestBuilder.getContent().stream()
          .filter(c -> c instanceof Element
              && ("query-param".equals(((Element) c).getName()) || "query-params".equals(((Element) c).getName())))
          .collect(toList()));

      List<Content> builderContent = listBuilder.build();

      requestBuilder.setContent(emptyList());
      object.addContent(builderContent);
    }

    Element responseValidator = null;
    Element successValidator = object.getChild("success-status-code-validator", httpNamespace);
    if (successValidator != null) {
      object.removeContent(successValidator);

      responseValidator = new Element("response-validator", httpNamespace);
      object.addContent(responseValidator);

      responseValidator.addContent(successValidator);
    }
    Element failureValidator = object.getChild("failure-status-code-validator", httpNamespace);
    if (failureValidator != null) {
      object.removeContent(failureValidator);

      if (responseValidator == null) {
        responseValidator = new Element("response-validator", httpNamespace);
        object.addContent(responseValidator);
      }

      responseValidator.addContent(failureValidator);
    }
  }

  private void addAttributesToInboundProperties(Element object, MigrationReport report) {
    migrateOperationStructure(getApplicationModel(), object, report);

    Map<String, String> expressionsPerProperty = new LinkedHashMap<>();
    expressionsPerProperty.put("http.status", "message.attributes.statusCode");
    expressionsPerProperty.put("http.reason", "message.attributes.reasonPhrase");
    expressionsPerProperty.put("http.headers", "message.attributes.headers");

    try {
      addAttributesMapping(getApplicationModel(), "org.mule.extension.http.api.HttpResponseAttributes", expressionsPerProperty);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void executeChild(Element object, MigrationReport report, Namespace httpNamespace) throws RuntimeException {
    object.getChildren().forEach(c -> {
      if (HTTP_NAMESPACE.equals(c.getNamespaceURI())) {
        executeChild(c, report, httpNamespace);
      }
    });

    if ("request-builder".equals(object.getName())) {
      handleReferencedRequestBuilder(object, httpNamespace);
      object.addContent(compatibilityHeaders(httpNamespace));
    }
  }

  private Element compatibilityHeaders(Namespace httpNamespace) {
    return new Element("headers", httpNamespace)
        .setText("#[vars.compatibility_outboundProperties filterObject ((value,key) -> not ((key as String) matches /http\\..*|Connection|Host|Transfer-Encoding/i))]");
  }

  private void handleReferencedRequestBuilder(Element object, final Namespace httpNamespace) {
    Element builderRef = object.getChild("builder", httpNamespace);
    int idx = 0;
    while (builderRef != null) {

      object.removeContent(builderRef);

      Element builder =
          getElementsFromDocument(object.getDocument(),
                                  "/mule:mule/http:request-builder[@name='" + builderRef.getAttributeValue("ref") + "']").get(0);

      handleReferencedRequestBuilder(builder, httpNamespace);
      List<Element> builderContent = ImmutableList.copyOf(builder.getChildren()).asList();
      builder.setContent(emptyList());
      builder.getParent().removeContent(builder);

      object.addContent(idx, builderContent);
      idx += builderContent.size();

      builderRef = object.getChild("builder", httpNamespace);
    }
  }
}