package com.lsy.idea.util;


import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author liuweiping
 * @date 2026-06-25
 **/
public class JsonUtilTest {
    
    @Test
    public void testJsonToObject() {
        String json = "{\"name\":\"张三\",\"age\":20}";
        Person person = JsonUtils.fromJson(json, Person.class);
        assertNotNull(person, "person should not be null");
    }
    
    public static class Person {
        
        private String name;
        
        private Integer age;
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public Integer getAge() {
            return age;
        }
        
        public void setAge(Integer age) {
            this.age = age;
        }
    }
}
