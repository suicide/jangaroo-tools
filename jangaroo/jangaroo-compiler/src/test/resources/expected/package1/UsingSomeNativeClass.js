define("as3/package1/UsingSomeNativeClass",["module","exports","as3-rt/AS3","native!package1@acme/native","as3/package1/someOtherPackage/SomeNativeClass"], function($module,$exports,AS3,$$1,package1$someOtherPackage$SomeNativeClass) { AS3.compilationUnit($module,$exports,function($primaryDeclaration){/*package package1 {
import package1.someOtherPackage.SomeNativeClass;

/**
 * This is an example of a class using a "native" class.
 * /
public class UsingSomeNativeClass {

  public var someNative:package1.SomeNativeClass =*/function someNative_(){this.someNative=( new $$1.SomeNativeClass());}/*;
  public native function get someNative2():package1.SomeNativeClass;

  public*/ function UsingSomeNativeClass() {var this$=this;someNative_.call(this);
    new package1$someOtherPackage$SomeNativeClass._();
    this.someNative.setBaz ( "foo");
    this.someNative2.setBaz ( "foo");
    var local = function package1$UsingSomeNativeClass$16_17()/*:void*/ {
      var test/*:String*/ = this$.someNative2.getBaz();
    };
    var foo = this.getFoobar();
    var bar = this.getAnotherNativeAccessor();
  }/*

  [Accessor("getFoobar")]
  public*/ function get$someNativeAccessor()/*:package1.SomeNativeClass*/ {
    return this.someNative;
  }/*

  [Accessor]
  public*/ function get$anotherNativeAccessor()/*:package1.SomeNativeClass*/ {
    return this.someNative;
  }/*

  [Accessor]
  public*/ function get$monkey()/*:Boolean*/ {
    return false;
  }/*

  [Accessor]
  public*/ function set$monkey(value/*:Boolean*/)/*:void*/ {
  }/*
}
}

============================================== Jangaroo part ==============================================*/
    $primaryDeclaration(AS3.class_($module, {members: {
      constructor: UsingSomeNativeClass,
      getFoobar: get$someNativeAccessor,
      getAnotherNativeAccessor: get$anotherNativeAccessor,
      isMonkey: get$monkey,
      setMonkey: set$monkey
    }}));
  });
});