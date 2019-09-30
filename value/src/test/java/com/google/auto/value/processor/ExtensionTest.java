/*
 * Copyright 2015 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.auto.value.processor;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.testing.compile.JavaSourcesSubject.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.extension.AutoValueExtension;
import com.google.auto.value.extension.AutoValueExtension.BuilderContext;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import com.google.testing.compile.CompileTester.SuccessfulCompilationClause;
import com.google.testing.compile.JavaFileObjects;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import javax.annotation.processing.Filer;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExtensionTest {

  @Test
  public void testExtensionCompilation() throws Exception {

    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "}");
    JavaFileObject expectedExtensionOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz",
            "package foo.bar;",
            "",
            "final class AutoValue_Baz extends $AutoValue_Baz {",
            "  public AutoValue_Baz(String foo) {",
            "    super(foo);",
            "  }",
            "  @Override public String foo() {",
            "    return \"foo\";",
            "  }",
            "  public String dizzle() {\n",
            "    return \"dizzle\";\n",
            "  }",
            "}");
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutError()
        .and()
        .generatesSources(expectedExtensionOutput);
  }

  @Test
  public void testExtensionConsumesProperties() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "  abstract String dizzle();",
            "}");
    JavaFileObject expectedExtensionOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.$AutoValue_Baz",
            "package foo.bar;",
            "",
            GeneratedImport.importGeneratedAnnotationType(),
            "",
            "@Generated(\"com.google.auto.value.processor.AutoValueProcessor\")",
            " abstract class $AutoValue_Baz extends Baz {",
            "",
            "  private final String foo;",
            "",
            "  $AutoValue_Baz(",
            "      String foo) {",
            "    if (foo == null) {",
            "      throw new NullPointerException(\"Null foo\");",
            "    }",
            "    this.foo = foo;",
            "  }",
            "",
            "  @Override",
            "  String foo() {",
            "    return foo;",
            "  }",
            "",
            "  @Override",
            "  public String toString() {",
            "    return \"Baz{\"",
            "        + \"foo=\" + foo",
            "        + \"}\";",
            "  }",
            "",
            "  @Override",
            "  public boolean equals(Object o) {",
            "    if (o == this) {",
            "      return true;",
            "    }",
            "    if (o instanceof Baz) {",
            "      Baz that = (Baz) o;",
            "      return this.foo.equals(that.foo());",
            "    }",
            "    return false;",
            "  }",
            "",
            "  @Override",
            "  public int hashCode() {",
            "    int h$ = 1;",
            "    h$ *= 1000003;",
            "    h$ ^= foo.hashCode();",
            "    return h$;",
            "  }",
            "}");
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutError()
        .and()
        .generatesSources(expectedExtensionOutput);
  }

  @Test
  public void testDoesntRaiseWarningForConsumedProperties() {
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue public abstract class Baz {",
            "  abstract String foo();",
            "  abstract String dizzle();",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder {",
            "    public abstract Builder foo(String s);",
            "    public abstract Baz build();",
            "  }",
            "}");
    assertThat(impl)
        .withCompilerOptions("-Xlint:-processing", "-implicit:class")
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutWarnings();
  }

  @Test
  public void testDoesntRaiseWarningForToBuilder() {
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue public abstract class Baz {",
            "  abstract String foo();",
            "  abstract String dizzle();",
            "  abstract Builder toBuilder();",
            "",
            "  @AutoValue.Builder",
            "  public abstract static class Builder {",
            "    public abstract Builder foo(String s);",
            "    public abstract Baz build();",
            "  }",
            "}");
    assertThat(impl)
        .withCompilerOptions("-Xlint:-processing", "-implicit:class")
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutWarnings();
  }

  @Test
  public void testCantConsumeTwice() throws Exception {
    class ConsumeDizzle extends NonFinalExtension {
      @Override
      public Set<String> consumeProperties(Context context) {
        return ImmutableSet.of("dizzle");
      }
    }
    class AlsoConsumeDizzle extends ConsumeDizzle {}
    AutoValueExtension ext1 = new ConsumeDizzle();
    AutoValueExtension ext2 = new AlsoConsumeDizzle();
    Truth.assertThat(ext1).isNotEqualTo(ext2);
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue public abstract class Baz {",
            "  abstract String foo();",
            "  abstract String dizzle();",
            "}");
    assertThat(impl)
        .processedWith(new AutoValueProcessor(ImmutableList.of(ext1, ext2)))
        .failsToCompile()
        .withErrorContaining("wants to consume a method that was already consumed")
        .in(impl)
        .onLine(5);
  }

  @Test
  public void testCantConsumeNonExistentProperty() throws Exception {
    class ConsumeDizzle extends NonFinalExtension {
      @Override
      public Set<String> consumeProperties(Context context) {
        return ImmutableSet.of("dizzle");
      }
    }
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue public abstract class Baz {",
            "  abstract String foo();",
            "}");
    assertThat(impl)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new ConsumeDizzle())))
        .failsToCompile()
        .withErrorContaining("wants to consume a property that does not exist: dizzle")
        .in(impl)
        .onLine(3);
  }

  @Test
  public void testCantConsumeConcreteMethod() throws Exception {
    class ConsumeConcreteMethod extends NonFinalExtension {
      @Override
      public Set<ExecutableElement> consumeMethods(Context context) {
        TypeElement autoValueClass = context.autoValueClass();
        for (ExecutableElement method :
            ElementFilter.methodsIn(autoValueClass.getEnclosedElements())) {
          if (method.getSimpleName().contentEquals("frob")) {
            return ImmutableSet.of(method);
          }
        }
        throw new AssertionError("Could not find frob method");
      }
    }
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue public abstract class Baz {",
            "  abstract String foo();",
            "  void frob(int x) {}",
            "}");
    assertThat(impl)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new ConsumeConcreteMethod())))
        .failsToCompile()
        .withErrorContaining(
            "wants to consume a method that is not one of the abstract methods in this class")
        .in(impl)
        .onLine(3)
        .and()
        .withErrorContaining("frob")
        .in(impl)
        .onLine(3);
  }

  @Test
  public void testCantConsumeNonExistentMethod() throws Exception {
    class ConsumeBogusMethod extends NonFinalExtension {
      @Override
      public Set<ExecutableElement> consumeMethods(Context context) {
        // Find Integer.intValue() and try to consume that.
        Elements elementUtils = context.processingEnvironment().getElementUtils();
        TypeElement javaLangInteger = elementUtils.getTypeElement(Integer.class.getName());
        for (ExecutableElement method :
            ElementFilter.methodsIn(javaLangInteger.getEnclosedElements())) {
          if (method.getSimpleName().contentEquals("intValue")) {
            return ImmutableSet.of(method);
          }
        }
        throw new AssertionError("Could not find Integer.intValue()");
      }
    }
    JavaFileObject impl =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "import com.google.auto.value.AutoValue;",
            "@AutoValue public abstract class Baz {",
            "  abstract String foo();",
            "}");
    assertThat(impl)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new ConsumeBogusMethod())))
        .failsToCompile()
        .withErrorContaining(
            "wants to consume a method that is not one of the abstract methods in this class")
        .in(impl)
        .onLine(3)
        .and()
        .withErrorContaining("intValue")
        .in(impl)
        .onLine(3);
  }

  @Test
  public void testExtensionWithoutConsumedPropertiesFails() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "  abstract String dizzle();",
            "  abstract Double[] bad();",
            "}");
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .failsToCompile()
        .withErrorContaining(
            "An @AutoValue class cannot define an array-valued property unless "
                + "it is a primitive array");
  }

  @Test
  public void testConsumeMethodWithArguments() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "  abstract void writeToParcel(Object parcel, int flags);",
            "}");
    assertThat(javaFileObject)
        .withCompilerOptions("-Xlint:-processing", "-implicit:class")
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FakeWriteToParcelExtension())))
        .compilesWithoutWarnings();
  }

  @Test
  public void testExtensionWithBuilderCompilation() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "  abstract String bar();",
            "",
            "  @AutoValue.Builder public static abstract class Builder {",
            "    public abstract Builder foo(String foo);",
            "    public abstract Builder bar(String bar);",
            "    public abstract Baz build();",
            "  }",
            "}");
    JavaFileObject expectedExtensionOutput =
        JavaFileObjects.forSourceLines(
            "foo.bar.AutoValue_Baz",
            "package foo.bar;",
            "",
            "final class AutoValue_Baz extends $AutoValue_Baz {",
            "  public AutoValue_Baz(String foo, String bar) {",
            "    super(foo, bar);",
            "  }",
            "  @Override public String foo() {",
            "    return \"foo\";",
            "  }",
            "  public String dizzle() {\n",
            "    return \"dizzle\";\n",
            "  }",
            "}");
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .compilesWithoutError()
        .and()
        .generatesSources(expectedExtensionOutput);
  }

  @Test
  public void testLastExtensionGeneratesNoCode() {
    doTestNoCode(new FooExtension(), new NonFinalExtension(), new SideFileExtension());
  }

  @Test
  public void testFirstExtensionGeneratesNoCode() {
    doTestNoCode(new SideFileExtension(), new FooExtension(), new NonFinalExtension());
  }

  @Test
  public void testMiddleExtensionGeneratesNoCode() {
    doTestNoCode(new FooExtension(), new SideFileExtension(), new NonFinalExtension());
  }

  @Test
  public void testLoneExtensionGeneratesNoCode() {
    doTestNoCode(new SideFileExtension());
  }

  private void doTestNoCode(AutoValueExtension... extensions) {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  public abstract String foo();",
            "",
            "  public static Baz create(String foo) {",
            "    return new AutoValue_Baz(foo);",
            "  }",
            "}");
    assertThat(javaFileObject)
        .withCompilerOptions("-Xlint:-processing", "-implicit:class")
        .processedWith(new AutoValueProcessor(ImmutableList.copyOf(extensions)))
        .compilesWithoutWarnings()
        .and()
        .generatesFileNamed(StandardLocation.SOURCE_OUTPUT, "foo.bar", "Side_Baz.java");
  }

  @Test
  public void testTwoExtensionsBothWantToBeFinal() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "}");
    assertThat(javaFileObject)
        .processedWith(
            new AutoValueProcessor(ImmutableList.of(new FooExtension(), new FinalExtension())))
        .failsToCompile()
        .withErrorContaining(
            "More than one extension wants to generate the final class: "
                + FooExtension.class.getName()
                + ", "
                + FinalExtension.class.getName())
        .in(javaFileObject)
        .onLine(6);
  }

  @Test
  public void testNonFinalThenFinal() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "}");
    FinalExtension finalExtension = new FinalExtension();
    NonFinalExtension nonFinalExtension = new NonFinalExtension();
    assertFalse(finalExtension.generated);
    assertFalse(nonFinalExtension.generated);
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(finalExtension, nonFinalExtension)))
        .compilesWithoutError();
    assertTrue(finalExtension.generated);
    assertTrue(nonFinalExtension.generated);
  }

  @Test
  public void testFinalThenNonFinal() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "}");
    FinalExtension finalExtension = new FinalExtension();
    NonFinalExtension nonFinalExtension = new NonFinalExtension();
    assertFalse(finalExtension.generated);
    assertFalse(nonFinalExtension.generated);
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(nonFinalExtension, finalExtension)))
        .compilesWithoutError();
    assertTrue(finalExtension.generated);
    assertTrue(nonFinalExtension.generated);
  }

  @Test
  public void testUnconsumedMethod() throws Exception {
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "  abstract String foo();",
            "  abstract void writeToParcel(Object parcel, int flags);",
            "}");
    assertThat(javaFileObject)
        .processedWith(new AutoValueProcessor(ImmutableList.of(new FooExtension())))
        .failsToCompile()
        .withErrorContaining("writeToParcel")
        .and()
        .withWarningContaining(
            "Abstract method is neither a property getter nor a Builder converter, "
                + "and no extension consumed it")
        .in(javaFileObject)
        .onLine(8);
    // The error here comes from the Java compiler rather than AutoValue, so we don't assume
    // much about what it looks like. On the other hand, the warning does come from AutoValue
    // so we know what to expect.
  }

  /**
   * Tests that the search for extensions doesn't completely blow AutoValue up if there is a corrupt
   * jar in the {@code processorpath}. If we're not careful, that can lead to a
   * ServiceConfigurationError.
   */
  @Test
  public void testBadJarDoesntBlowUp() throws IOException {
    File badJar = File.createTempFile("bogus", ".jar");
    try {
      doTestBadJarDoesntBlowUp(badJar);
    } finally {
      badJar.delete();
    }
  }

  private void doTestBadJarDoesntBlowUp(File badJar) throws IOException {
    FileOutputStream fileOutputStream = new FileOutputStream(badJar);
    JarOutputStream jarOutputStream = new JarOutputStream(fileOutputStream);
    byte[] bogusLine = "bogus line\n".getBytes("UTF-8");
    ZipEntry zipEntry = new ZipEntry("META-INF/services/" + AutoValueExtension.class.getName());
    zipEntry.setSize(bogusLine.length);
    jarOutputStream.putNextEntry(zipEntry);
    jarOutputStream.write(bogusLine);
    jarOutputStream.close();
    ClassLoader badJarLoader = new URLClassLoader(new URL[] {badJar.toURI().toURL()});
    JavaFileObject javaFileObject =
        JavaFileObjects.forSourceLines(
            "foo.bar.Baz",
            "package foo.bar;",
            "",
            "import com.google.auto.value.AutoValue;",
            "",
            "@AutoValue",
            "public abstract class Baz {",
            "}");
    SuccessfulCompilationClause success =
        assertThat(javaFileObject)
            .withCompilerOptions("-Xlint:-processing", "-implicit:class")
            .processedWith(new AutoValueProcessor(badJarLoader))
            .compilesWithoutError();
    success.withWarningContaining(
        "This may be due to a corrupt jar file in the compiler's classpath.\n  "
            + ServiceConfigurationError.class.getName());
    success
        .and()
        .generatesFileNamed(StandardLocation.SOURCE_OUTPUT, "foo.bar", "AutoValue_Baz.java");
  }

  private static final String CUSTOM_OPTION = "customAnnotation.customOption";

  /**
   * Tests that extensions providing their own (annotated) annotation types or options get picked
   * up.
   */
  @Test
  public void extensionsWithAnnotatedOptions() {
    ExtensionWithAnnotatedOptions extension = new ExtensionWithAnnotatedOptions();

    // Ensure default annotation support works
    assertThat(extension.getSupportedOptions()).contains(CUSTOM_OPTION);

    // Ensure it's carried over to the AutoValue processor
    assertThat(new AutoValueProcessor(ImmutableList.of(extension)).getSupportedOptions())
        .contains(CUSTOM_OPTION);
  }

  /**
   * Tests that extensions providing their own implemented annotation types or options get picked
   * up.
   */
  @Test
  public void extensionsWithImplementedOptions() {
    ExtensionWithImplementedOptions extension = new ExtensionWithImplementedOptions();

    // Ensure it's carried over to the AutoValue processor
    assertThat(new AutoValueProcessor(ImmutableList.of(extension)).getSupportedOptions())
        .contains(CUSTOM_OPTION);
  }

  @SupportedOptions(CUSTOM_OPTION)
  static class ExtensionWithAnnotatedOptions extends AutoValueExtension {
    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      return null;
    }
  }

  static class ExtensionWithImplementedOptions extends AutoValueExtension {
    @Override
    public Set<String> getSupportedOptions() {
      return ImmutableSet.of(CUSTOM_OPTION);
    }

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      return null;
    }
  }

  private static class FooExtension extends AutoValueExtension {

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public boolean mustBeFinal(Context context) {
      return true;
    }

    @Override
    public Set<String> consumeProperties(Context context) {
      if (context.properties().containsKey("dizzle")) {
        return ImmutableSet.of("dizzle");
      } else {
        return Collections.emptySet();
      }
    }

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      StringBuilder constructor =
          new StringBuilder().append("  public ").append(className).append("(");

      boolean first = true;
      for (Map.Entry<String, ExecutableElement> el : context.properties().entrySet()) {
        if (first) {
          first = false;
        } else {
          constructor.append(", ");
        }
        constructor.append("String ").append(el.getKey());
      }

      constructor.append(") {\n");
      constructor.append("    super(");

      first = true;
      for (Map.Entry<String, ExecutableElement> el : context.properties().entrySet()) {
        if (first) {
          first = false;
        } else {
          constructor.append(", ");
        }
        constructor.append(el.getKey());
      }
      constructor.append(");\n");
      constructor.append("  }\n");

      return String.format(
          "package %s;\n"
              + "\n"
              + "%s class %s extends %s {\n"
              + constructor
              + "  @Override public String foo() {\n"
              + "    return \"foo\";\n"
              + "  }\n"
              + "  public String dizzle() {\n"
              + "    return \"dizzle\";\n"
              + "  }\n"
              + "}",
          context.packageName(),
          isFinal ? "final" : "abstract",
          className,
          classToExtend);
    }
  }

  // Extension that generates a class that just forwards to the parent constructor.
  // We will make subclasses that are respectively final and non-final.
  private abstract static class EmptyExtension extends AutoValueExtension {
    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public abstract boolean mustBeFinal(Context context);

    String extraText(Context context) {
      return "";
    }

    boolean generated = false;

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      generated = true;

      ImmutableList.Builder<String> typesAndNamesBuilder = ImmutableList.builder();
      context.propertyTypes().forEach((name, type) -> typesAndNamesBuilder.add(type + " " + name));
      String typesAndNames = Joiner.on(", ").join(typesAndNamesBuilder.build());
      String template =
          "package {pkg};\n"
              + "\n"
              + "{finalOrAbstract} class {className} extends {classToExtend} {\n"
              + "  {className}({propertyTypesAndNames}) {\n"
              + "    super({propertyNames});\n"
              + "  }\n"
              + "  {extraText}\n"
              + "}\n";
      return template
          .replace("{pkg}", context.packageName())
          .replace("{finalOrAbstract}", isFinal ? "final" : "abstract")
          .replace("{className}", className)
          .replace("{classToExtend}", classToExtend)
          .replace("{propertyTypesAndNames}", typesAndNames)
          .replace("{propertyNames}", Joiner.on(", ").join(context.properties().keySet()))
          .replace("{extraText}", extraText(context));
    }
  }

  private static class NonFinalExtension extends EmptyExtension {
    @Override
    public boolean mustBeFinal(Context context) {
      return false;
    }
  }

  private static class FinalExtension extends EmptyExtension {
    @Override
    public boolean mustBeFinal(Context context) {
      return true;
    }
  }

  private static class SideFileExtension extends AutoValueExtension {
    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public boolean mustBeFinal(Context context) {
      return false;
    }

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      String sideClassName = "Side_" + context.autoValueClass().getSimpleName().toString();
      String sideClass =
          "" //
          + "package " + context.packageName() + ";\n"
          + "class " + sideClassName + " {}\n";
      Filer filer = context.processingEnvironment().getFiler();
      try {
        String sideClassFqName = context.packageName() + "." + sideClassName;
        JavaFileObject sourceFile =
            filer.createSourceFile(sideClassFqName, context.autoValueClass());
        try (Writer sourceWriter = sourceFile.openWriter()) {
          sourceWriter.write(sideClass);
        }
      } catch (IOException e) {
        context
            .processingEnvironment()
            .getMessager()
            .printMessage(Diagnostic.Kind.ERROR, e.toString());
      }
      return null;
    }
  }

  private static class FakeWriteToParcelExtension extends NonFinalExtension {
    private ExecutableElement writeToParcelMethod(Context context) {
      for (ExecutableElement method : context.abstractMethods()) {
        if (method.getSimpleName().contentEquals("writeToParcel")) {
          return method;
        }
      }
      throw new AssertionError("Did not see abstract method writeToParcel");
    }

    @Override
    public Set<ExecutableElement> consumeMethods(Context context) {
      return ImmutableSet.of(writeToParcelMethod(context));
    }

    @Override
    String extraText(Context context) {
      // This is perhaps overgeneral. It is simply going to generate this:
      // @Override void writeToParcel(Object parcel, int flags) {}
      ExecutableElement methodToImplement = writeToParcelMethod(context);
      assertThat(methodToImplement.getReturnType().getKind()).isEqualTo(TypeKind.VOID);
      ImmutableList.Builder<String> typesAndNamesBuilder = ImmutableList.builder();
      for (VariableElement p : methodToImplement.getParameters()) {
        typesAndNamesBuilder.add(p.asType() + " " + p.getSimpleName());
      }
      return "@Override void "
          + methodToImplement.getSimpleName()
          + "("
          + Joiner.on(", ").join(typesAndNamesBuilder.build())
          + ") {}";
    }
  }

  @Test
  public void propertyTypes() {
    JavaFileObject parent = JavaFileObjects.forSourceLines(
        "foo.bar.Parent",
        "package foo.bar;",
        "",
        "import java.util.List;",
        "",
        "interface Parent<T> {",
        "  T thing();",
        "  List<T> list();",
        "}");
    JavaFileObject autoValueClass = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "",
        "@AutoValue",
        "abstract class Baz implements Parent<String> {",
        "}");
    ContextChecker checker =
        context -> {
          assertThat(context.builder()).isEmpty();
          Map<String, TypeMirror> propertyTypes = context.propertyTypes();
          assertThat(propertyTypes.keySet()).containsExactly("thing", "list");
          TypeMirror thingType = propertyTypes.get("thing");
          assertThat(thingType).isNotNull();
          assertThat(thingType.getKind()).isEqualTo(TypeKind.DECLARED);
          assertThat(MoreTypes.asTypeElement(thingType).getQualifiedName().toString())
              .isEqualTo("java.lang.String");
          TypeMirror listType = propertyTypes.get("list");
          assertThat(listType).isNotNull();
          assertThat(listType.toString()).isEqualTo("java.util.List<java.lang.String>");
        };
    ContextCheckingExtension extension = new ContextCheckingExtension(checker);
    assertThat(autoValueClass, parent)
        .processedWith(new AutoValueProcessor(ImmutableList.of(extension)))
        .compilesWithoutError();
  }

  @Test
  public void builderContext() {
    JavaFileObject parent = JavaFileObjects.forSourceLines(
        "foo.bar.Parent",
        "package foo.bar;",
        "",
        "import com.google.common.collect.ImmutableList;",
        "",
        "interface Parent<T> {",
        "  T thing();",
        "  ImmutableList<T> list();",
        "}");
    JavaFileObject autoValueClass = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import com.google.common.collect.ImmutableList;",
        "",
        "@AutoValue",
        "abstract class Baz implements Parent<String> {",
        "  static Builder builder() {",
        "    return new AutoValue_Baz.Builder();",
        "  }",
        "",
        "  abstract Builder toBuilder();",
        "",
        "  @AutoValue.Builder",
        "  abstract static class Builder {",
        "    abstract Builder setThing(String x);",
        "    abstract Builder setList(Iterable<String> x);",
        "    abstract Builder setList(ImmutableList<String> x);",
        "    abstract ImmutableList.Builder<String> listBuilder();",
        "    abstract Baz autoBuild();",
        "    Baz build() {",
        "      return autoBuild();",
        "    }",
        "  }",
        "}");
    ContextChecker checker =
        context -> {
          assertThat(context.builder()).isPresent();
          BuilderContext builderContext = context.builder().get();

          assertThat(builderContext.builderType().getQualifiedName().toString())
              .isEqualTo("foo.bar.Baz.Builder");

          Set<ExecutableElement> builderMethods = builderContext.builderMethods();
          assertThat(builderMethods).hasSize(1);
          ExecutableElement builderMethod = Iterables.getOnlyElement(builderMethods);
          assertThat(builderMethod.getSimpleName().toString()).isEqualTo("builder");

          Set<ExecutableElement> toBuilderMethods = builderContext.toBuilderMethods();
          assertThat(toBuilderMethods).hasSize(1);
          ExecutableElement toBuilderMethod = Iterables.getOnlyElement(toBuilderMethods);
          assertThat(toBuilderMethod.getSimpleName().toString()).isEqualTo("toBuilder");

          Optional<ExecutableElement> buildMethod = builderContext.buildMethod();
          assertThat(buildMethod).isPresent();
          assertThat(buildMethod.get().getSimpleName().toString()).isEqualTo("build");
          assertThat(buildMethod.get().getParameters()).isEmpty();
          assertThat(buildMethod.get().getReturnType().toString()).isEqualTo("foo.bar.Baz");

          ExecutableElement autoBuildMethod = builderContext.autoBuildMethod();
          assertThat(autoBuildMethod.getSimpleName().toString()).isEqualTo("autoBuild");
          assertThat(autoBuildMethod.getModifiers()).contains(Modifier.ABSTRACT);
          assertThat(autoBuildMethod.getParameters()).isEmpty();
          assertThat(autoBuildMethod.getReturnType().toString()).isEqualTo("foo.bar.Baz");

          Map<String, Set<ExecutableElement>> setters = builderContext.setters();
          assertThat(setters.keySet()).containsExactly("thing", "list");
          Set<ExecutableElement> thingSetters = setters.get("thing");
          assertThat(thingSetters).hasSize(1);
          ExecutableElement thingSetter = Iterables.getOnlyElement(thingSetters);
          assertThat(thingSetter.getSimpleName().toString()).isEqualTo("setThing");
          Set<ExecutableElement> listSetters = setters.get("list");
          assertThat(listSetters).hasSize(2);
          for (ExecutableElement listSetter : listSetters) {
            assertThat(listSetter.getSimpleName().toString()).isEqualTo("setList");
          }

          Map<String, ExecutableElement> propertyBuilders = builderContext.propertyBuilders();
          assertThat(propertyBuilders.keySet()).containsExactly("list");
          assertThat(propertyBuilders.get("list").getSimpleName().toString())
              .isEqualTo("listBuilder");
        };
    ContextCheckingExtension extension = new ContextCheckingExtension(checker);
    assertThat(autoValueClass, parent)
        .processedWith(new AutoValueProcessor(ImmutableList.of(extension)))
        .compilesWithoutError();
  }

  @Test
  public void oddBuilderContext() {
    JavaFileObject autoValueClass = JavaFileObjects.forSourceLines(
        "foo.bar.Baz",
        "package foo.bar;",
        "",
        "import com.google.auto.value.AutoValue;",
        "import com.google.common.collect.ImmutableList;",
        "",
        "@AutoValue",
        "abstract class Baz {",
        "  abstract String string();",
        "",
        "  @AutoValue.Builder",
        "  abstract static class Builder {",
        "    abstract Builder setString(String x);",
        "    abstract Baz oddBuild();",
        "    Baz build(int butNotReallyBecauseOfThisParameter) {",
        "      return null;",
        "    }",
        "  }",
        "}");
    ContextChecker checker =
        context -> {
          assertThat(context.builder()).isPresent();
          BuilderContext builderContext = context.builder().get();
          assertThat(builderContext.builderMethods()).isEmpty();
          assertThat(builderContext.toBuilderMethods()).isEmpty();
          assertThat(builderContext.buildMethod()).isEmpty();
          assertThat(builderContext.autoBuildMethod().getSimpleName().toString())
              .isEqualTo("oddBuild");

          Map<String, Set<ExecutableElement>> setters = builderContext.setters();
          assertThat(setters.keySet()).containsExactly("string");
          Set<ExecutableElement> thingSetters = setters.get("string");
          assertThat(thingSetters).hasSize(1);
          ExecutableElement thingSetter = Iterables.getOnlyElement(thingSetters);
          assertThat(thingSetter.getSimpleName().toString()).isEqualTo("setString");

          assertThat(builderContext.propertyBuilders()).isEmpty();
        };
    ContextCheckingExtension extension = new ContextCheckingExtension(checker);
    assertThat(autoValueClass)
        .processedWith(new AutoValueProcessor(ImmutableList.of(extension)))
        .compilesWithoutError();
  }

  private interface ContextChecker extends Consumer<AutoValueExtension.Context> {}

  private static class ContextCheckingExtension extends AutoValueExtension {
    private final Consumer<Context> checker;

    ContextCheckingExtension(Consumer<Context> checker) {
      this.checker = checker;
    }

    @Override
    public boolean applicable(Context context) {
      return true;
    }

    @Override
    public String generateClass(
        Context context, String className, String classToExtend, boolean isFinal) {
      checker.accept(context);
      return null;
    }
  }
}
