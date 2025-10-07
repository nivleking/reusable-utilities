package com.nivleking.springboot.utils;

import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;

public class TestUtils {
    public static void setField(Object target, String fieldName, Object value) {
        ReflectionTestUtils.setField(target, fieldName, value);
    }
}