package net.jangaroo.exml.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jangaroo.exml.utils.ExmlUtils;
import net.jangaroo.jooc.Jooc;
import net.jangaroo.jooc.backend.ActionScriptCodeGeneratingModelVisitor;
import net.jangaroo.jooc.backend.JsCodeGenerator;
import net.jangaroo.jooc.model.AnnotationModel;
import net.jangaroo.jooc.model.AnnotationPropertyModel;
import net.jangaroo.jooc.model.ClassModel;
import net.jangaroo.jooc.model.CompilationUnitModelRegistry;
import net.jangaroo.jooc.model.CompilationUnitModel;
import net.jangaroo.jooc.model.FieldModel;
import net.jangaroo.jooc.model.MemberModel;
import net.jangaroo.jooc.model.MethodModel;
import net.jangaroo.jooc.model.MethodType;
import net.jangaroo.jooc.model.ParamModel;
import net.jangaroo.jooc.model.PropertyModel;
import net.jangaroo.utils.AS3Type;
import net.jangaroo.utils.CompilerUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generate ActionScript 3 APIs from a jsduck JSON export of the Ext JS 4.x API.
 */
public class ExtAsApiGenerator {

  private static Map<String,ExtClass> extClasses;
  private static CompilationUnitModelRegistry compilationUnitModelRegistry;
  private static Set<String> interfaces;
  public static final List<String> NON_COMPILE_TIME_CONSTANT_INITIALIZERS = Arrays.asList("window", "document", "document.body");

  public static void main(String[] args) throws IOException {
    File srcDir = new File(args[0]);
    File outputDir = new File(args[1]);
    File[] files = srcDir.listFiles();
    if (files != null) {
      extClasses = new HashMap<String, ExtClass>();
      compilationUnitModelRegistry = new CompilationUnitModelRegistry();
      interfaces = new HashSet<String>();
      for (File jsonFile : files) {
        ExtClass extClass = readExtApiJson(jsonFile);
        if (extClass != null) {
          extClasses.put(extClass.name, extClass);
          for (String alternateClassName : extClass.alternateClassNames) {
            extClasses.put(alternateClassName, extClass);
          }
          for (String mixin : extClass.mixins) {
            interfaces.add(mixin);
          }
        }
      }
      markTransitiveSupersAsInterfaces(interfaces);
      //markTransitiveSupersAsInterfaces(singletons);
      for (ExtClass extClass : new HashSet<ExtClass>(extClasses.values())) {
        generateClassModel(extClass);
      }
      compilationUnitModelRegistry.complementOverrides();
      compilationUnitModelRegistry.complementImports();
      for (CompilationUnitModel compilationUnitModel : compilationUnitModelRegistry.getCompilationUnitModels()) {
        generateActionScriptCode(compilationUnitModel, outputDir);
      }
    }
  }

  private static void markTransitiveSupersAsInterfaces(Set<String> extClasses) {
    Set<String> supers = supers(extClasses);
    while (interfaces.addAll(supers)) {
      supers = supers(supers);
    }
  }

  private static Set<String> supers(Set<String> extClasses) {
    Set<String> result = new HashSet<String>();
    for (String extClass : extClasses) {
      String superclass = ExtAsApiGenerator.extClasses.get(extClass).extends_;
      if (superclass != null) {
        result.add(superclass);
      }
    }
    return result;
  }

  private static ExtClass readExtApiJson(File jsonFile) throws IOException {
    System.out.printf("Reading API from %s...\n", jsonFile.getPath());
    ExtClass extClass = new ObjectMapper().readValue(jsonFile, ExtClass.class);
    if (JsCodeGenerator.PRIMITIVES.contains(extClass.name)) {
      System.err.println("ignoring built-in class " + extClass.name);
      return null;
    }
    return extClass;
  }

  private static void generateClassModel(ExtClass extClass) {
    CompilationUnitModel extAsClassUnit = createClassModel(convertType(extClass.name));
    ClassModel extAsClass = (ClassModel)extAsClassUnit.getPrimaryDeclaration();
    System.out.printf("Generating AS3 API model %s for %s...%n", extAsClassUnit.getQName(), extClass.name);
    //configClass.setName(extClass.aliases.get("widget").get(0));
    //configClass.setPackage("ext.config");
    extAsClass.setAsdoc(toAsDoc(extClass.doc));
    CompilationUnitModel extAsInterfaceUnit = null;
    if (interfaces.contains(extClass.name)) {
      extAsInterfaceUnit = createClassModel(convertToInterface(extClass.name));
      System.out.printf("Generating AS3 API model %s for %s...%n", extAsInterfaceUnit.getQName(), extClass.name);
      ClassModel extAsInterface = (ClassModel)extAsInterfaceUnit.getPrimaryDeclaration();
      extAsInterface.setInterface(true);
      extAsInterface.setAsdoc(toAsDoc(extClass.doc));
      addInterfaceForSuperclass(extClass, extAsInterface);
    }
    if (isSingleton(extClass)) {
      FieldModel singleton = new FieldModel(CompilerUtils.className(extClass.name), extAsClassUnit.getQName());
      singleton.setConst(true);
      singleton.setValue("new " + extAsClassUnit.getQName());
      singleton.setAsdoc(extAsClass.getAsdoc());
      CompilationUnitModel singletonUnit = new CompilationUnitModel(extAsClassUnit.getPackage(), singleton);
      compilationUnitModelRegistry.register(singletonUnit);

      extAsClass.setAsdoc(String.format("%s\n<p>Type of singleton %s.</p>\n * @see %s %s",
        extAsClass.getAsdoc(),
        singleton.getName(),
        CompilerUtils.qName(extAsClassUnit.getPackage(), "#" + singleton.getName()),
        singletonUnit.getQName()));
    }
    extAsClass.setSuperclass(convertType(extClass.extends_));
    if (extAsInterfaceUnit != null) {
      extAsClass.addInterface(extAsInterfaceUnit.getQName());
    }
    for (String mixin : extClass.mixins) {
      String superInterface = convertToInterface(mixin);
      extAsClass.addInterface(superInterface);
      if (extAsInterfaceUnit != null) {
        extAsInterfaceUnit.getClassModel().addInterface(superInterface);
      }
    }

    if (extAsInterfaceUnit != null) {
      addNonStaticMembers(extClass, extAsInterfaceUnit);
    }

    if (!extAsClass.isInterface()) {
      addFields(extAsClass, filterByOwner(false, extClass, extClass.statics.property));
      addMethods(extAsClass, filterByOwner(false, extClass, extClass.statics.method));
    }
    addNonStaticMembers(extClass, extAsClassUnit);
  }

  private static void addInterfaceForSuperclass(ExtClass extClass, ClassModel extAsInterface) {
    if (extClass.extends_ != null) {
      extAsInterface.addInterface(convertToInterface(extClass.extends_));
    }
  }

  private static CompilationUnitModel createClassModel(String qName) {
    CompilationUnitModel compilationUnitModel = new CompilationUnitModel(null, new ClassModel());
    compilationUnitModel.setQName(qName);
    compilationUnitModelRegistry.register(compilationUnitModel);
    return compilationUnitModel;
  }

  private static void addNonStaticMembers(ExtClass extClass, CompilationUnitModel extAsClassUnit) {
    addEvents(extAsClassUnit, filterByOwner(false, extClass, extClass.members.event));
    ClassModel extAsClass = extAsClassUnit.getClassModel();
    addProperties(extAsClass, filterByOwner(extAsClass.isInterface(), extClass, extClass.members.property));
    addMethods(extAsClass, filterByOwner(extAsClass.isInterface(), extClass, extClass.members.method));
    addProperties(extAsClass, filterByOwner(extAsClass.isInterface(), extClass, extClass.members.cfg));
  }

  private static void generateActionScriptCode(CompilationUnitModel extAsClass, File outputDir) throws IOException {
    File outputFile = CompilerUtils.fileFromQName(extAsClass.getQName(), outputDir, Jooc.AS_SUFFIX);
    //noinspection ResultOfMethodCallIgnored
    outputFile.getParentFile().mkdirs(); // NOSONAR
    System.out.printf("Generating AS3 API for %s into %s...\n", extAsClass.getQName(), outputFile.getPath());
    extAsClass.visit(new ActionScriptCodeGeneratingModelVisitor(new FileWriter(outputFile)));
  }

  private static <T extends Member> List<T> filterByOwner(boolean isInterface, ExtClass owner, List<T> members) {
    List<T> result = new ArrayList<T>();
    for (T member : members) {
      if (member.owner.equals(owner.name) && (!isInterface || isPublicNonStaticMethod(member))) {
        result.add(member);
      }
    }
    return result;
  }

  private static boolean isPublicNonStaticMethod(Member member) {
    return member instanceof Method && !member.meta.static_ && !member.meta.private_ && !member.meta.protected_
      && !"constructor".equals(member.name);
  }

  private static boolean isConst(Member member) {
    return member.meta.readonly || (member.name.equals(member.name.toUpperCase()) && member.default_ != null);
  }

  private static void addEvents(CompilationUnitModel compilationUnitModel, List<Event> events) {
    for (Event event : events) {
      ClassModel classModel = compilationUnitModel.getClassModel();
      String eventTypeQName = generateEventClass(compilationUnitModel, event);
      AnnotationModel annotationModel = new AnnotationModel("Event",
              new AnnotationPropertyModel("name", "'" + event.name + "'"),
              new AnnotationPropertyModel("type", "'" + eventTypeQName + "'"));
      annotationModel.setAsdoc(toAsDoc(event.doc) + String.format("\n * @eventType %s.NAME", eventTypeQName));
      classModel.addAnnotation(annotationModel);
      System.err.println("*** adding event " + event.name + " to class " + classModel.getName());
    }
  }

  public static String capitalize(String name) {
    return name == null || name.length() == 0 ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private static String generateEventClass(CompilationUnitModel compilationUnitModel, Event event) {
    ClassModel classModel = compilationUnitModel.getClassModel();
    String eventTypeQName = CompilerUtils.qName(compilationUnitModel.getPackage(),
            "events." + classModel.getName() + capitalize(event.name) + "Event");
    CompilationUnitModel extAsClassUnit = createClassModel(eventTypeQName);
    ClassModel extAsClass = (ClassModel)extAsClassUnit.getPrimaryDeclaration();
    extAsClass.setAsdoc(toAsDoc(event.doc) + "\n * @see " + compilationUnitModel.getQName());

    FieldModel eventNameConstant = new FieldModel("NAME", "String", CompilerUtils.quote(event.name));
    eventNameConstant.setStatic(true);
    eventNameConstant.setAsdoc(MessageFormat.format("This constant defines the value of the <code>type</code> property of the event object\nfor a <code>{0}</code> event.\n   * @eventType {0}", event.name));
    extAsClass.addMember(eventNameConstant);

    MethodModel constructorModel = extAsClass.createConstructor();
    constructorModel.addParam(new ParamModel("arguments", "Array"));
    StringBuilder propertyAssignments = new StringBuilder();
    for (int i = 0; i < event.params.size(); i++) {
      Param param = event.params.get(i);

      // add assignment to constructor body:
      if (i > 0) {
        propertyAssignments.append("\n    ");
      }
      propertyAssignments.append(String.format("this['%s'] = arguments[%d];", convertName(param.name), i));

      // add getter method:
      MethodModel property = new MethodModel(MethodType.GET, convertName(param.name), convertType(param.type));
      property.setAsdoc(toAsDoc(param.doc));
      extAsClass.addMember(property);
    }

    constructorModel.setBody(propertyAssignments.toString());

    return eventTypeQName;
  }

  private static void addFields(ClassModel classModel, List<? extends Member> fields) {
    for (Member member : fields) {
      FieldModel fieldModel = new FieldModel(convertName(member.name), convertType(member.type), member.default_);
      fieldModel.setAsdoc(toAsDoc(member.doc));
      setStatic(fieldModel, member);
      fieldModel.setConst(isConst(member));
      classModel.addMember(fieldModel);
    }
  }

  private static void addProperties(ClassModel classModel, List<? extends Member> properties) {
    for (Member member : properties) {
      if (classModel.getMember(member.name) == null) {
        PropertyModel propertyModel = new PropertyModel(convertName(member.name), convertType(member.type));
        propertyModel.setAsdoc(toAsDoc(member.doc));
        setStatic(propertyModel, member);
        propertyModel.addGetter();
        if (!member.meta.readonly) {
          propertyModel.addSetter();
        }
        classModel.addMember(propertyModel);
      }
    }
  }

  private static void addMethods(ClassModel classModel, List<Method> methods) {
    for (Method method : methods) {
      if (classModel.getMember(method.name) == null) {
        boolean isConstructor = method.name.equals("constructor");
        MethodModel methodModel = isConstructor
                ? new MethodModel(classModel.getName(), null)
                : new MethodModel(convertName(method.name), convertType(method.return_.type));
        methodModel.setAsdoc(toAsDoc(method.doc));
        methodModel.getReturnModel().setAsdoc(toAsDoc(method.return_.doc));
        setStatic(methodModel, method);
        for (Param param : method.params) {
          ParamModel paramModel = new ParamModel(convertName(param.name), convertType(param.type));
          paramModel.setAsdoc(toAsDoc(param.doc));
          setDefaultValue(paramModel, param);
          paramModel.setRest(param == method.params.get(method.params.size() - 1) && param.type.endsWith("..."));
          methodModel.addParam(paramModel);
        }
        classModel.addMember(methodModel);
      }
    }
  }

  private static void setStatic(MemberModel memberModel, Member member) {
    memberModel.setStatic(member.meta.static_ || isStaticSingleton(extClasses.get(member.owner)));
  }

  private static String toAsDoc(String doc) {
    String asDoc = doc.trim();
    if (asDoc.startsWith("<p>")) {
      // remove <p>...</p> around first paragraph:
      int endTagPos = asDoc.indexOf("</p>");
      asDoc = asDoc.substring(3, endTagPos) + asDoc.substring(endTagPos + 4);
    }
    if (asDoc.startsWith("{")) {
      int closingBracePos = asDoc.indexOf("} ");
      if (closingBracePos != -1) {
        asDoc = asDoc.substring(closingBracePos + 2);
      }
    }
    return asDoc;
  }

  private static void setDefaultValue(ParamModel paramModel, Param param) {
    String defaultValue = param.default_;
    if (defaultValue != null) {
      if (NON_COMPILE_TIME_CONSTANT_INITIALIZERS.contains(defaultValue)) {
        paramModel.setAsdoc("(Default " + defaultValue + ") " + paramModel.getAsdoc());
        defaultValue = null;
        param.optional = true; // only in case it is set inconsistently...
      }
    }
    if (defaultValue == null && param.optional) {
      defaultValue = AS3Type.getDefaultValue(paramModel.getType());
    }
    paramModel.setValue(defaultValue);
  }

  private static String convertName(String name) {
    return "is".equals(name) ? "matches" :
            "class".equals(name) ? "cls" :
                    "this".equals(name) ? "source" :
                            "new".equals(name) ? "new_" :
                            name;
  }

  private static String convertToInterface(String mixin) {
    String packageName = CompilerUtils.packageName(mixin).toLowerCase();
    String className = "I" + CompilerUtils.className(mixin);
    if (packageName.startsWith("ext")) {
      packageName = "ext4" + packageName.substring(3);
    }
    return CompilerUtils.qName(packageName, className);
  }

  private static String convertType(String extType) {
    if (extType == null) {
      return null;
    }
    if ("undefined".equals(extType)) {
      return "void";
    }
    if ("HTMLElement".equals(extType) || "Event".equals(extType)) {
      return "js." + extType;
    }
    if (extType.endsWith("...")) {
      return "Array";
    }
    if (!extType.matches("[a-zA-Z0-9._$<>]+") || "Mixed".equals(extType)) {
      return "*"; // TODO: join types? rather use Object? simulate overloading by splitting into several methods?
    }
    ExtClass extClass = extClasses.get(extType);
    if (extClass != null) {
      // normalize:
      extType = extClass.name;
    }
    if ("Ext".equals(extType)) {
      // special case: move singleton "Ext" into package "ext":
      extType = "ext.Ext";
    }
    String packageName = CompilerUtils.packageName(extType).toLowerCase();
    String className = CompilerUtils.className(extType);
    if (isSingleton(extClass)) {
      className = "S" + className;
    }
    if (JsCodeGenerator.PRIMITIVES.contains(className)) {
      if ("ext".equals(packageName)) {
        if (isStaticSingleton(extClass)) {
          // for most built-in classes, there is a static ...Util class:
          packageName = "ext.util";
          className += "Util";
        } else {
          // all others in package "ext" are prefixed with "Ext":
          className = "Ext" + className;
        }
      } else {
        // all in other packages are postfixed with the upper-cased last package segment:
        className += ExmlUtils.createComponentClassName(packageName.substring(packageName.lastIndexOf('.') + 1));
      }
    } else if ("is".equals(className)) {
      // special case lower-case "is" class:
      className = "Is";
    }
    if (packageName.startsWith("ext")) {
      packageName = "ext4" + packageName.substring(3);
    }
    return CompilerUtils.qName(packageName, className);
  }

  private static boolean isSingleton(ExtClass extClass) {
    return extClass != null && extClass.singleton && !isStaticSingleton(extClass);
  }

  private static boolean isStaticSingleton(ExtClass extClass) {
    return extClass != null && extClass.singleton && (extClass.extends_ == null || extClass.extends_.length() == 0)
      && extClass.statics.cfg.isEmpty() && extClass.statics.event.isEmpty() && extClass.statics.method.isEmpty()
      && extClass.statics.property.isEmpty() && extClass.statics.css_mixin.isEmpty() && extClass.statics.css_var.isEmpty();
  }

  @SuppressWarnings("UnusedDeclaration")
  @JsonIgnoreProperties({"html_meta", "html_type"})
  public static class Tag {
    public String tagname;
    public String name;
    public String doc;
    @JsonProperty("private")
    public String private_;
    public MemberReference inheritdoc;

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Tag tag = (Tag)o;
      return name.equals(tag.name) && !(tagname != null ? !tagname.equals(tag.tagname) : tag.tagname != null);

    }

    @Override
    public int hashCode() {
      int result = tagname != null ? tagname.hashCode() : 0;
      result = 31 * result + name.hashCode();
      return result;
    }
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class ExtClass extends Tag {
    @JsonProperty("extends")
    public String extends_;
    public List<String> mixins;
    public List<String> alternateClassNames;
    public Map<String,List<String>> aliases;
    public boolean singleton;
    public List<String> requires;
    public List<String> uses;
    public String code_type;
    public boolean inheritable;
    public Meta meta;
    public String id;
    public Members members;
    public Members statics;
    public List<Object> files;
    public boolean component;
    public List<String> superclasses;
    public List<String> subclasses;
    public List<String> mixedInto;
    public List<String> parentMixins;
    @JsonProperty("abstract")
    public boolean abstract_;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Members {
    public List<Member> cfg;
    public List<Property> property;
    public List<Method> method;
    public List<Event> event;
    public List<Member> css_var;
    public List<Member> css_mixin;
  }

  @JsonIgnoreProperties({"html_type", "html_meta", "properties"})
  public abstract static class Var extends Tag {
    public String type;
    @JsonProperty("default")
    public String default_;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Member extends Var {
    public String owner;
    public String shortDoc;
    public Meta meta;
    public boolean inheritable;
    public String id;
    public List<String> files;
    public boolean accessor;
    public boolean evented;
    public List<Overrides> overrides;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class MemberReference extends Tag {
    public String cls;
    public String member;
    public String type;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Overrides {
    public String name;
    public String owner;
    public String id;
  }

  public static class Property extends Member {
    @Override
    public String toString() {
      return meta + "var " + super.toString();
    }
  }

  public static class Param extends Var {
    public boolean optional;
  }

  public static class Method extends Member {
    public List<Param> params;
    @JsonProperty("return")
    public Param return_;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Event extends Member {
    public List<Param> params;
  }

  @SuppressWarnings("UnusedDeclaration")
  public static class Meta {
    @JsonProperty("protected")
    public boolean protected_;
    @JsonProperty("private")
    public boolean private_;
    public boolean readonly;
    @JsonProperty("static")
    public boolean static_;
    @JsonProperty("abstract")
    public boolean abstract_;
    public boolean markdown;
    public Map<String,String> deprecated;
    public String template;
    public List<String> author;
    public List<String> docauthor;
    public boolean required;
    public Map<String,String> removed;
  }
}
