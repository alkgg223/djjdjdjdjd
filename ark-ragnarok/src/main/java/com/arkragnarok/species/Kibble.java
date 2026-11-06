package com.arkragnarok.species;

/** the six kibble tiers; each species prefers one of them */
public enum Kibble {
    BASIC("basic_kibble"), SIMPLE("simple_kibble"), REGULAR("regular_kibble"), SUPERIOR("superior_kibble"),
    EXCEPTIONAL("exceptional_kibble"), EXTRAORDINARY("extraordinary_kibble");

    public final String item;

    Kibble(String item) {
        this.item = item;
    }
}
