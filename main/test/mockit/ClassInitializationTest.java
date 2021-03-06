/*
 * Copyright (c) 2006 Rogério Liesenfeld
 * This file is subject to the terms of the MIT license (see LICENSE.txt).
 */
package mockit;

import static org.junit.Assert.*;
import org.junit.*;

public final class ClassInitializationTest
{
   static final class ClassWhichFailsAtInitialization
   {
      static
      {
         //noinspection ConstantIfStatement,ConstantConditions
         if (true) {
            throw new AssertionError();
         }
      }

      static int value() { return 0; }
   }

   @Test
   public void usingMockUp()
   {
      new MockUp<ClassWhichFailsAtInitialization>() {
         @Mock void $clinit() {}
         @Mock int value() { return 1; }
      };

      assertEquals(1, ClassWhichFailsAtInitialization.value());
   }

   @Test
   public void stubbingOutClassInitializerOnly()
   {
      new MockUp<ClassWhichFailsAtInitialization>() { @Mock void $clinit() {} };

      assertEquals(0, ClassWhichFailsAtInitialization.value());
   }

   @Test
   public void usingExpectations(@Mocked(stubOutClassInitialization = true) ClassWhichFailsAtInitialization unused)
   {
      new Expectations() {{
         ClassWhichFailsAtInitialization.value(); result = 1;
      }};

      assertEquals(1, ClassWhichFailsAtInitialization.value());
   }

   static class ClassWithStaticInitializer1
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static String variable;
      static { variable = doSomething(); }
      static String doSomething() { return "real value"; }
   }

   @Test
   public void mockClassWithStaticInitializerNotStubbedOut(@Mocked ClassWithStaticInitializer1 mocked)
   {
      assertNotNull(ClassWithStaticInitializer1.CONSTANT);
      assertNull(ClassWithStaticInitializer1.doSomething());
      assertEquals("real value", ClassWithStaticInitializer1.variable);
   }

   static class ClassWithStaticInitializer2
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
   }

   @Test
   public void useClassWithStaticInitializerNeverStubbedOutAndNotMockedNow()
   {
      // Allows the class to be initialized without throwing the exception.
      MockUp<?> mockUp = new MockUp<ClassWithStaticInitializer2>() { @Mock void doSomething() {} };

      // Initializes the class:
      assertNotNull(ClassWithStaticInitializer2.CONSTANT);

      // Restore the now initialized class:
      mockUp.tearDown();

      try {
         ClassWithStaticInitializer2.doSomething();
         fail();
      }
      catch (UnsupportedOperationException ignore) {}
   }

   static class AnotherClassWithStaticInitializer1
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
      int getValue() { return -1; }
   }

   @Test
   public void mockClassWithStaticInitializerStubbedOut(
      @Mocked(stubOutClassInitialization = true) AnotherClassWithStaticInitializer1 mockAnother)
   {
      assertNull(AnotherClassWithStaticInitializer1.CONSTANT);
      AnotherClassWithStaticInitializer1.doSomething();
      assertEquals(0, mockAnother.getValue());
   }

   static class AnotherClassWithStaticInitializer2
   {
      static final String CONSTANT = new String("not a compile-time constant");
      static { doSomething(); }
      static void doSomething() { throw new UnsupportedOperationException("must not execute"); }
      int getValue() { return -1; }
   }

   @Test
   public void useClassWithStaticInitializerPreviouslyStubbedOutButNotMockedNow()
   {
      // Stubs out the static initializer, initializes the class, and then restores it:
      MockUp<?> mockUp = new MockUp<AnotherClassWithStaticInitializer2>() { @Mock void $clinit() {} };
      assertNull(AnotherClassWithStaticInitializer2.CONSTANT);
      mockUp.tearDown();

      try {
         AnotherClassWithStaticInitializer2.doSomething();
         fail();
      }
      catch (UnsupportedOperationException ignore) {}

      assertEquals(-1, new AnotherClassWithStaticInitializer2().getValue());
   }

   static class ClassWhichCallsStaticMethodFromInitializer
   {
      static {
         String s = someValue();
         s.length();
      }

      static String someValue() { return "some value"; }
   }

   @Test
   public void mockUninitializedClass(@Mocked ClassWhichCallsStaticMethodFromInitializer unused)
   {
      assertNull(ClassWhichCallsStaticMethodFromInitializer.someValue());
   }

   static class ClassWhichCallsStaticMethodFromInitializer2
   {
      static { someValue().length(); }
      static String someValue() { return "some value"; }
   }

   @Test
   public void partiallyMockUninitializedClass()
   {
      new Expectations(ClassWhichCallsStaticMethodFromInitializer2.class) {{
         ClassWhichCallsStaticMethodFromInitializer2.someValue();
      }};

      assertNull(ClassWhichCallsStaticMethodFromInitializer2.someValue());
   }

   public interface BaseType { String someValue(); }
   static final class NestedImplementationClass implements BaseType
   {
      static { new NestedImplementationClass().someValue().length(); }
      @Override public String someValue() { return "some value"; }
   }

   @Before
   public void loadNestedImplementationClass() throws Exception
   {
      // Ensure the class gets loaded, but not initialized, before it gets mocked.
      // The HotSpot VM would (for some reason) already have loaded it, but the J9 VM would not.
      NestedImplementationClass.class.getName();
   }

   @Test
   public void mockUninitializedImplementationClass(@Capturing BaseType mockBase)
   {
      BaseType obj = new NestedImplementationClass();

      assertNull(obj.someValue());
   }

   static class Dependency { static Dependency create() { return null; } }
   static class Dependent
   {
      static final Dependency DEPENDENCY = Dependency.create();
      static { DEPENDENCY.toString(); }
   }
   static class AnotherDependent
   {
      static final Dependency DEPENDENCY = Dependency.create();
      static { DEPENDENCY.toString(); }
   }

   @Mocked Dependency dependency;
   @Mocked Dependent dependent;

   @Test
   public void mockAnotherDependentClass(@Mocked AnotherDependent anotherDependent)
   {
      assertNotNull(Dependent.DEPENDENCY);
      assertNotNull(AnotherDependent.DEPENDENCY);
   }

   static class Dependency2 { static Dependency2 create() { return new Dependency2(); } }
   static class Dependent2
   {
      static final Dependency2 DEPENDENCY = Dependency2.create();
      static { DEPENDENCY.toString(); }
   }

   @Test
   public void partiallyMockBothDependencyAndDependentClasses()
   {
      new Expectations(Dependency2.class, Dependent2.class) {};

      assertNotNull(Dependent2.DEPENDENCY);
   }

   static class Dependency3 { static Dependency3 create() { return null; } }
   static class Dependent3
   {
      static final Dependency3 DEPENDENCY = Dependency3.create();
      static { DEPENDENCY.toString(); }
   }

   @Test
   public void partiallyMockDependencyClassThenDependentClass()
   {
      final Dependency3 dep = new Dependency3();
      new Expectations(Dependency3.class) {{ Dependency3.create(); result = dep; }};
      new Expectations(Dependent3.class) {};

      assertSame(dep, Dependency3.create());
      assertNotNull(Dependent3.DEPENDENCY);
   }

   public interface BaseInterface { String DO_NOT_REMOVE = new String("Testing"); }
   public interface SubInterface extends BaseInterface {}
   @Mocked SubInterface mock;

   @Test
   public void verifyClassInitializerForMockedBaseInterface()
   {
      assertNotNull(mock);
      assertEquals("Testing", BaseInterface.DO_NOT_REMOVE);
   }
}
