package com.fg.mail.smtp.client.request.filter;

public class Eq {

    private UrlPathPart property;
    private String value;

    public Eq(UrlPathPart property, String value) {
        this.property = property;
        this.value = value;
    }

    public String getPropertyName() {
        return property.getName();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Eq equals = (Eq) o;

        if (property != equals.property) return false;
        if (value != null ? !value.equals(equals.value) : equals.value != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = property != null ? property.getName().hashCode() : 0;
        result = 31 * result + (value != null ? value.hashCode() : 0);
        return result;
    }
}