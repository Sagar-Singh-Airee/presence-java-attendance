/**
 * Purpose: Represents one saved face photo.
 * Why it is used: It links each training photo to the person it belongs to.
 */
package com.presence.model;

public record FaceSample(long id, long personId, String imagePath) { }
