package com.intuit.karate.graal;

import com.intuit.karate.Match;
import com.intuit.karate.core.MockUtils;
import com.intuit.karate.http.Request;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
class JsEngineTest {

    static final Logger logger = LoggerFactory.getLogger(JsEngineTest.class);

    JsEngine je;

    @BeforeEach
    void beforeEach() {
        je = JsEngine.global();
    }

    @AfterEach
    void afterEach() {
        JsEngine.remove();
    }

    @Test
    void testFunctionExecute() {
        JsValue v = je.eval("(function(){ return ['a', 'b', 'c'] })");
        assertTrue(v.isFunction());
        JsValue res = v.invoke();
        assertTrue(res.isArray());
        assertEquals("[\"a\",\"b\",\"c\"]", res.toJson());
        assertEquals("function(){ return ['a', 'b', 'c'] }", v.toString());
    }

    @Test
    void testArrowFunctionZeroArg() {
        JsValue v = je.eval("() => ['a', 'b', 'c']");
        assertTrue(v.isFunction());
        JsValue res = v.invoke();
        assertTrue(res.isArray());
        assertEquals("[\"a\",\"b\",\"c\"]", res.toJson());
        assertEquals("() => ['a', 'b', 'c']", v.toString());
    }

    @Test
    void testJsFunctionToJavaFunction() {
        Value v = je.evalForValue("() => 'hello'");
        assertTrue(v.canExecute());
        Function temp = (Function) v.as(Object.class);
        String res = (String) temp.apply(null);
        assertEquals(res, "hello");
        v = je.evalForValue("(a, b) => a + b");
        assertTrue(v.canExecute());
        temp = v.as(Function.class);
        Number num = (Number) temp.apply(new Object[]{1, 2});
        assertEquals(num, 3);
    }

    @Test
    void testArrowFunctionReturnsObject() {
        Value v = je.evalForValue("() => { a: 1 }");
        assertTrue(v.canExecute());
        Value res = v.execute();
        // curly braces are interpreted as code blocks :(
        assertTrue(res.isNull());
        v = je.evalForValue("() => ({ a: 1 })");
        assertTrue(v.canExecute());
        res = v.execute();
        Match.that(res.as(Map.class)).isEqualTo("{ a: 1 }");
    }

    @Test
    void testArrowFunctionSingleArg() {
        JsValue v = je.eval("x => [x, x]");
        assertTrue(v.isFunction());
        JsValue res = v.invoke(1);
        assertTrue(res.isArray());
        assertEquals("[1,1]", res.toJson());
        assertEquals("x => [x, x]", v.toString());
    }

    @Test
    void testFunctionVariableExecute() {
        je.eval("var add = function(a, b){ return a + b }");
        JsValue jv = je.eval("add(1, 2)");
        assertEquals(jv.<Integer>getValue(), 3);
    }

    @Test
    void testJavaInterop() {
        je.eval("var SimplePojo = Java.type('com.intuit.karate.graal.SimplePojo')");
        JsValue sp = je.eval("new SimplePojo()");
        Value ov = sp.getOriginal();
        assertTrue(ov.isHostObject());
        SimplePojo o = ov.as(SimplePojo.class);
        assertEquals(null, o.getFoo());
        assertEquals(0, o.getBar());
    }

    @Test
    void testJsOperations() {
        je.eval("var foo = { a: 1 }");
        JsValue v = je.eval("foo.a");
        Object val = v.getValue();
        assertEquals(val, 1);
    }

    @Test
    void testMapOperations() {
        Map<String, Object> map = new HashMap();
        map.put("foo", "bar");
        map.put("a", 1);
        map.put("child", Collections.singletonMap("baz", "ban"));
        je.put("map", map);
        JsValue v1 = je.eval("map.foo");
        assertEquals(v1.getValue(), "bar");
        JsValue v2 = je.eval("map.a");
        assertEquals(v2.<Integer>getValue(), 1);
        JsValue v3 = je.eval("map.child");
        assertEquals(v3.getValue(), Collections.singletonMap("baz", "ban"));
        JsValue v4 = je.eval("map.child.baz");
        assertEquals(v4.getValue(), "ban");
    }

    @Test
    void testListOperations() {
        je.eval("var temp = [{a: 1}, {b: 2}]");
        JsValue temp = je.eval("temp");
        je.put("items", temp.getValue());
        je.eval("items.push({c: 3})");
        JsValue items = je.eval("items");
        assertTrue(items.isArray());
        assertEquals(3, items.getAsList().size());
        je.eval("items.splice(0, 1)");
        items = je.eval("items");
        assertEquals(2, items.getAsList().size());
    }

    @Test
    void testRequestObject() {
        Request request = new Request();
        request.setMethod("GET");
        request.setPath("/index");
        Map<String, List<String>> params = new HashMap();
        params.put("hello", Collections.singletonList("world"));
        request.setParams(params);
        je.put("request", request);
        JsValue jv = je.eval("request.params['hello']");
        assertEquals(jv.getAsList(), Collections.singletonList("world"));
        jv = je.eval("request.param('hello')");
        assertEquals(jv.getValue(), "world");
    }

    @Test
    void testBoolean() {
        assertFalse(je.eval("1 == 2").isTrue());
        assertTrue(je.eval("1 == 1").isTrue());
    }

    @Test
    void testStringInterpolation() {
        je.put("name", "John");
        JsValue temp = je.eval("`hello ${name}`");
        assertEquals(temp.getValue(), "hello John");
    }

    @Test
    void testHostBytes() {
        JsValue v = je.eval("Java.type('com.intuit.karate.core.MockUtils')");
        je.put("Utils", v.getValue());
        JsValue val = je.eval("Utils.testBytes");
        assertEquals(MockUtils.testBytes, val.getOriginal().asHostObject());
    }

    @Test
    void testValueAndHostObject() {
        SimplePojo sp = new SimplePojo();
        Value v = Value.asValue(sp);
        assertTrue(v.isHostObject());
    }

    @Test
    void testEvalWithinFunction() {
        Map<String, Object> map = new HashMap();
        map.put("a", 1);
        map.put("b", 2);
        String src = "a + b";
        Value function = je.evalForValue("x => { var a = x.a; var b = x.b; return " + src + "; }");
        assertTrue(function.canExecute());
        Value result = function.execute(JsValue.fromJava(map));
        assertEquals(result.asInt(), 3);
    }

}
