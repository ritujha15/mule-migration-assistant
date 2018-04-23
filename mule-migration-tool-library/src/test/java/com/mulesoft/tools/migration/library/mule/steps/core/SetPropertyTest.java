/*
 * Copyright (c) 2017 MuleSoft, Inc. This software is protected under international
 * copyright law. All use of this software is subject to MuleSoft's Master Subscription
 * Agreement (or other master license agreement) separately entered into in writing between
 * you and MuleSoft. If such an agreement is not in place, you may not use the software.
 */
package com.mulesoft.tools.migration.library.mule.steps.core;

import static com.mulesoft.tools.migration.helper.DocumentHelper.getDocument;
import static com.mulesoft.tools.migration.helper.DocumentHelper.getElementsFromDocument;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.mock;

import com.mulesoft.tools.migration.exception.MigrationStepException;
import com.mulesoft.tools.migration.step.category.MigrationReport;

import org.jdom2.Document;
import org.jdom2.Element;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class SetPropertyTest {

  private static final String FILE_SAMPLE_XML = "setProperty.xml";
  private static final Path FILE_EXAMPLES_PATH = Paths.get("mule/examples/core");
  private static final Path FILE_SAMPLE_PATH = FILE_EXAMPLES_PATH.resolve(FILE_SAMPLE_XML);

  private SetProperty setProperty;
  private Element node;

  @Before
  public void setUp() throws Exception {
    setProperty = new SetProperty();
  }

  @Ignore
  @Test(expected = MigrationStepException.class)
  public void executeWithNullElement() throws Exception {
    setProperty.execute(null, mock(MigrationReport.class));
  }

  @Test
  public void execute() throws Exception {
    Document doc = getDocument(this.getClass().getClassLoader().getResource(FILE_SAMPLE_PATH.toString()).toURI().getPath());
    node = getElementsFromDocument(doc, setProperty.getAppliedTo().getExpression()).get(0);
    setProperty.execute(node, mock(MigrationReport.class));

    assertThat("The node namespace didn't change", node.getNamespaceURI(),
               is("http://www.mulesoft.org/schema/mule/compatibility"));
    assertThat("The node namespace prefix didn't change", node.getNamespacePrefix(), is("compatibility"));
    assertThat("The node name changed", node.getName(), is("set-property"));
    assertThat("The attribute was renamed", node.getAttribute("propertyName"), is(notNullValue()));
  }
}