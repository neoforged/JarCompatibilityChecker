package com.example.test;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.URLConnection;

public class TestClassA {
    public String field;
    public Runnable lambdaThing = () -> {
        System.out.println("Hi!");
    };
    public void publicMethodA(String p1) {

    }

    public static void doIt(JsonObject obj) {

    }
}
