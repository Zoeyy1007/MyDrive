/*
 * PHASE 0 - HTTP EXERCISE: Response DTO
 *
 * Package: com.mydrive.drive.hello.dto
 * Type to create: public record HelloResponse
 *
 * Give the record one component:
 *   String message
 *
 * Responsibility:
 *   This DTO describes the JSON returned to the browser. Spring/Jackson will
 *   turn it into JSON resembling {"message":"Hello from MyDrive!"}.
 *
 * Do not add HTTP annotations or business logic here.
 */
package com.mydrive.drive.hello.dto;
public record HelloResponse(String message) {
}