/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.accumulo.core.conf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class generates documentation to inform users of the available configuration properties in a
 * presentable form.
 */
public class ConfigurationDocGen {
  private abstract class Format {

    abstract void beginSection(String section);

    void endSection() {}

    void generate() {
      pageHeader();

      beginSection("Available Properties");
      propertyQuickLinks();
      for (Property prefix : prefixes) {
        if (!prefix.isExperimental()) {
          prefixSection(prefix);
          for (Property prop : sortedProps.values()) {
            if (!prop.isExperimental()) {
              property(prefix, prop);
            }
          }
        }
      }
      endSection();

      beginSection("Property Types");
      propertyTypeDescriptions();
      endSection();

      pageFooter();
      doc.close();
    }

    abstract String getExt();

    void pageFooter() {}

    // read static header content from resources and output
    void pageHeader() {
      appendResource("config-header." + getExt());
      doc.println();
    }

    abstract void prefixSection(Property prefix);

    abstract void property(Property prefix, Property prop);

    abstract void propertyTypeDescriptions();

    abstract void propertyQuickLinks();

    abstract String sanitize(String str);

  }

  private class HTML extends Format {
    private boolean highlight;

    private void beginRow() {
      doc.println(startTag("tr", highlight ? "class='highlight'" : null));
      highlight = !highlight;
    }

    @Override
    void beginSection(String section) {
      doc.println(t("h1", section, null));
      highlight = true;
      doc.println("<table>");
    }

    private String t(String tag, String cell, String options) {
      return startTag(tag, options) + cell + "</" + tag + ">";
    }

    private void cellData(String cell, String options) {
      doc.println(t("td", cell, options));
    }

    private void columnNames(String... names) {
      beginRow();
      for (String s : names)
        doc.println(t("th", s, null));
      endRow();
    }

    private void endRow() {
      doc.println("</tr>");
    }

    @Override
    void endSection() {
      doc.println("</table>");
    }

    @Override
    String getExt() {
      return "html";
    }

    @Override
    void pageFooter() {
      doc.println("</body>");
      doc.println("</html>");
    }

    @Override
    void propertyQuickLinks() {
      doc.println("<p>Jump to: ");
      String delimiter = "";
      for (Property prefix : prefixes) {
        if (!prefix.isExperimental()) {
          doc.print(delimiter + "<a href='#" + prefix.name() + "'>" + prefix.getKey() + "*</a>");
          delimiter = "&nbsp;|&nbsp;";
        }
      }
      doc.println("</p>");
    }

    @Override
    void prefixSection(Property prefix) {
      beginRow();
      doc.println(
          t("td", t("span", prefix.getKey() + '*', "id='" + prefix.name() + "' class='large'"),
              "colspan='5'" + (prefix.isDeprecated() ? " class='deprecated'" : "")));
      endRow();
      beginRow();
      doc.println(t("td",
          (prefix.isDeprecated() ? t("b", t("i", "Deprecated. ", null), null) : "")
              + sanitize(prefix.getDescription()),
          "colspan='5'" + (prefix.isDeprecated() ? " class='deprecated'" : "")));
      endRow();

      switch (prefix) {
        case TABLE_CONSTRAINT_PREFIX:
          break;
        case TABLE_ITERATOR_PREFIX:
          break;
        case TABLE_LOCALITY_GROUP_PREFIX:
          break;
        case TABLE_COMPACTION_STRATEGY_PREFIX:
          break;
        default:
          columnNames("Property", "Type", "ZooKeeper Mutable", "Default Value", "Description");
          break;
      }
    }

    @Override
    void property(Property prefix, Property prop) {
      boolean isDeprecated = prefix.isDeprecated() || prop.isDeprecated();
      if (prop.getKey().startsWith(prefix.getKey())) {
        beginRow();
        cellData(prop.getKey(), isDeprecated ? "class='deprecated'" : null);
        cellData(
            "<b><a href='#" + prop.getType().name() + "'>"
                + prop.getType().toString().replaceAll(" ", "&nbsp;") + "</a></b>",
            isDeprecated ? "class='deprecated'" : null);
        cellData(isZooKeeperMutable(prop), isDeprecated ? "class='deprecated'" : null);
        cellData(
            "<pre>" + (prop.getRawDefaultValue().isEmpty() ? "&nbsp;"
                : sanitize(prop.getRawDefaultValue().replaceAll(" ", "&nbsp;"))) + "</pre>",
            isDeprecated ? "class='deprecated'" : null);
        cellData(
            (isDeprecated ? "<b><i>Deprecated.</i></b> " : "") + sanitize(prop.getDescription()),
            isDeprecated ? "class='deprecated'" : null);
        endRow();
      }

    }

    @Override
    void propertyTypeDescriptions() {
      columnNames("Property Type", "Description");
      for (PropertyType type : PropertyType.values()) {
        if (type == PropertyType.PREFIX)
          continue;
        beginRow();
        cellData("<h3 id='" + type.name() + "'>" + type + "</h3>", null);
        cellData(type.getFormatDescription(), null);
        endRow();
      }
    }

    @Override
    String sanitize(String str) {
      return str.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
          .replaceAll("(?:\r\n|\r|\n)", "<br />");
    }

    private String startTag(String tag, String options) {
      return "<" + tag + (options != null ? " " + options : "") + ">";
    }

  }

  private class Asciidoc extends Format {
    @Override
    void beginSection(String section) {
      doc.println("=== " + section);
    }

    @Override
    String getExt() {
      return "txt";
    }

    @Override
    void propertyQuickLinks() {
      doc.println("Jump to: ");
      String delimiter = "";
      for (Property prefix : prefixes) {
        if (!prefix.isExperimental()) {
          doc.print(delimiter + "<<" + prefix.name() + ">>");
          delimiter = " | ";
        }
      }
      doc.println();
      doc.println();
    }

    @Override
    void prefixSection(Property prefix) {
      boolean depr = prefix.isDeprecated();
      doc.println("[[" + prefix.name() + "]]");
      doc.println("==== " + prefix.getKey() + "*" + (depr ? " (Deprecated)" : ""));
      doc.println(strike((depr ? "_Deprecated._ " : "") + sanitize(prefix.getDescription()), depr));
      doc.println();
    }

    @Override
    void property(Property prefix, Property prop) {
      boolean depr = prefix.isDeprecated() || prop.isDeprecated();
      if (prop.getKey().startsWith(prefix.getKey())) {
        doc.println("===== " + prop.getKey());
        doc.println(strike((depr ? "_Deprecated._ " : "") + sanitize(prop.getDescription()), depr));
        doc.println();
        doc.println(strike("_Type:_ " + prop.getType().name(), depr) + " +");
        doc.println(strike("_Zookeeper Mutable:_ " + isZooKeeperMutable(prop), depr) + " +");
        String defaultValue = sanitize(prop.getRawDefaultValue()).trim();
        if (defaultValue.length() == 0) {
          // need a placeholder or the asciidoc line break won't work
          defaultValue = strike("_Default Value:_ _empty_", depr);
        } else if (defaultValue.contains("\n")) {
          // deal with multi-line values, skip strikethrough of value
          defaultValue = strike("_Default Value:_ ", depr) + "\n----\n" + defaultValue + "\n----\n";
        } else {
          defaultValue = strike("_Default Value:_ " + "`" + defaultValue + "`", depr);
        }
        doc.println(defaultValue);
        doc.println();
      }
    }

    private String strike(String s, boolean isDeprecated) {
      return (isDeprecated ? "[line-through]#" : "") + s + (isDeprecated ? "#" : "");
    }

    @Override
    void propertyTypeDescriptions() {
      for (PropertyType type : PropertyType.values()) {
        if (type == PropertyType.PREFIX)
          continue;
        doc.println("==== " + sanitize(type.toString()));
        doc.println(sanitize(type.getFormatDescription()));
        doc.println();
      }
    }

    @Override
    String sanitize(String str) {
      return str;
    }
  }

  private static final Logger log = LoggerFactory.getLogger(ConfigurationDocGen.class);

  private PrintStream doc;

  private final ArrayList<Property> prefixes;

  private final TreeMap<String,Property> sortedProps;

  ConfigurationDocGen(PrintStream doc) {
    this.doc = doc;
    this.prefixes = new ArrayList<>();
    this.sortedProps = new TreeMap<>();

    for (Property prop : Property.values()) {
      if (prop.isExperimental())
        continue;

      if (prop.getType() == PropertyType.PREFIX)
        this.prefixes.add(prop);
      else
        this.sortedProps.put(prop.getKey(), prop);
    }
  }

  private void appendResource(String resourceName) {
    InputStream data = ConfigurationDocGen.class.getResourceAsStream(resourceName);
    if (data != null) {
      byte[] buffer = new byte[1024];
      int n;
      try {
        while ((n = data.read(buffer)) > 0)
          doc.print(new String(buffer, 0, n, UTF_8));
      } catch (IOException e) {
        log.debug("Encountered IOException while reading InputStream in appendResource().", e);
        return;
      } finally {
        try {
          data.close();
        } catch (IOException ex) {
          log.error("{}", ex.getMessage(), ex);
        }
      }
    }
  }

  private String isZooKeeperMutable(Property prop) {
    if (!Property.isValidZooPropertyKey(prop.getKey()))
      return "no";
    if (Property.isFixedZooPropertyKey(prop))
      return "yes but requires restart of the " + prop.getKey().split("[.]")[0];
    return "yes";
  }

  void generateHtml() {
    new HTML().generate();
  }

  void generateAsciidoc() {
    new Asciidoc().generate();
  }

  /**
   * Generates documentation for conf/accumulo-site.xml file usage. Arguments are: "--generate-doc",
   * file to write to.
   *
   * @param args
   *          command-line arguments
   *
   * @throws IllegalArgumentException
   *           if args is invalid
   */
  public static void main(String[] args)
      throws FileNotFoundException, UnsupportedEncodingException {
    if (args.length == 2 && args[0].equals("--generate-html")) {
      try (PrintStream p = new PrintStream(args[1], UTF_8.name())) {
        new ConfigurationDocGen(p).generateHtml();
      }
    } else if (args.length == 2 && args[0].equals("--generate-asciidoc")) {
      try (PrintStream p = new PrintStream(args[1], UTF_8.name())) {
        new ConfigurationDocGen(p).generateAsciidoc();
      }
    } else {
      throw new IllegalArgumentException("Usage: " + ConfigurationDocGen.class.getName()
          + " --generate-html <filename> | --generate-asciidoc <filename>");
    }
  }

}
