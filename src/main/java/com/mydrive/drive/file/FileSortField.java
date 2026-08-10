
package com.mydrive.drive.file;

public enum FileSortField {
    NAME("name"),
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    SIZE("size"),
    CONTENT_TYPE("contentType");

    private final String entityProperty;

    FileSortField(String entityProperty) {
        this.entityProperty = entityProperty;
    }

    public String getEntityProperty() {
        return entityProperty;
    }
}