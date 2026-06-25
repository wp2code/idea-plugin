package com.lsy.idea.util;

import org.apache.http.util.Asserts;
import org.junit.jupiter.api.Test;

/**
 * @author liuweiping
 * @date 2026-06-25
 **/
public class JsonUtilTest {
    
    @Test
    public void testJsonToObject() {
        String json = "{\"name\":\"张三\",\"age\":20}";
        Person person = JsonUtils.fromJson(json, Person.class);
        Asserts.notNull(person, "person should not be null");
    }
    
    public static class Person {
        
        private String name;
        
        private int age;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public int getAge() {
            return age;
        }
        
        public void setAge(int age) {
            this.age = age;
        }
    }
}
