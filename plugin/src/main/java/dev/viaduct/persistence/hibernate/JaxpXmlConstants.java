package dev.viaduct.persistence.hibernate;

import javax.xml.XMLConstants;

/** Exposes JDK JAXP constants without Gradle's legacy XML API shadowing Kotlin resolution. */
final class JaxpXmlConstants {
  private JaxpXmlConstants() {}

  static String accessExternalDtd() {
    return XMLConstants.ACCESS_EXTERNAL_DTD;
  }

  static String accessExternalSchema() {
    return XMLConstants.ACCESS_EXTERNAL_SCHEMA;
  }

  static String accessExternalStylesheet() {
    return XMLConstants.ACCESS_EXTERNAL_STYLESHEET;
  }
}
